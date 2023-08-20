(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer [chan go-loop <! go >! <!! close!]]
   [clojure.java.io :as io]
   [babashka.process :refer [process destroy-tree alive?]]
   [clojure.core.cache.wrapped :as w]
   [babashka.fs :as fs]
   [diehard.core :as dh]
   [vortext.esther.config :refer [errors]]
   [vortext.esther.util :refer
    [parse-maybe-json escape-newlines]]
   [clojure.string :as str])
  (:import [dev.failsafe TimeoutExceededException]))

(def ai-name "esther")

(defn named-role
  [entry]
  (if (= "assistant" (:role entry)) ai-name (:role entry)))

(defn as-role
  [entry]
  (str (str/capitalize (named-role entry))
       ": " (:content entry)))

(defn generate-prompt-str
  [submission]
  (str
   (:content (first submission)) ;; Instructions
   (str/join
    "\n"
    (for [entry (rest submission)] (as-role entry)))
   "\n"
   "Esther: "))

(defn parse
  [llama-output]
  (let [maybe-json (subs
                    llama-output
                    (str/index-of llama-output "{")
                    (inc (str/last-index-of llama-output "}")))]
    (parse-maybe-json (escape-newlines maybe-json))))

(defn safe-parse
  [llama-output]
  (try
    (parse llama-output)
    (catch Exception _ nil)))

(defn process-cmd
  [bin-dir model-path submission]
  (let [prompt (generate-prompt-str submission)
        tmp (str (fs/delete-on-exit (fs/create-temp-file)))
        model (str (fs/real-path (fs/path model-path)))]
    (spit tmp prompt)
    (str/join
     " "
     [(str (fs/real-path (fs/path bin-dir "main")))
      "-m" model
      "--simple-io"
      "-i"
      "-r" (str/capitalize "user: ")
      "-eps" "1e-5" ;; for best generation quality LLaMA 2
      ;;"-gqa" "8"    ;; for 70B models to work
      "--ctx-size" 2048
      "--threads" (.availableProcessors (Runtime/getRuntime))
      "-f" (str tmp)])))

(defn process-channels
  [cmd]
  (let [_ (log/debug "llama::process-channels:cmd" cmd)
        proc (process {:err :inherit
                       :shutdown destroy-tree}
                      cmd)
        out-ch (chan 32)
        in-ch (chan 32)
        rdr (io/reader (:out proc))]
    (go
      (with-open [wrtr (io/writer (:in proc))]
        (loop []
          (when-let [line (<! in-ch)]
            (.write wrtr (str line "\n"))
            (.flush wrtr)
            (recur)))))
    (go
      (try
        (loop []
          (when-let [line (.readLine rdr)]
            (>! out-ch line)
            (recur)))
        (finally
          (.close rdr))))
    {:proc proc
     :in-ch in-ch
     :out-ch out-ch}))


(defn llama-subprocess
  [bin-dir model-path submission]
  (let [subprocess (process-channels
                    (process-cmd bin-dir model-path submission))
        {:keys [proc out-ch _in-ch]} subprocess
        response-ch (chan 32)
        ;;pid (.pid (get-in subprocess [:proc :proc]))
        last-entry (:content (last submission))
        seen-last-entry? (atom false)]
    (go-loop []
      (if-let [line (<! out-ch)]
        (do (log/debug "llama::llama-subprocess:line" line)
            (when (str/includes? line last-entry)
              (reset! seen-last-entry? true))
            (if-let [json-obj (and @seen-last-entry?
                                   (str/includes? line (str/capitalize ai-name))
                                   (safe-parse line))]
              (if (:response json-obj)
                (do
                  (log/info "llama::llama-subprocess:json" json-obj)
                  ;;(shell "kill" "-INT" pid) ;; 2 - SIGINT - interupt process stream, ctrl-C
                  (>! response-ch json-obj)
                  (recur))
                (recur))
              (recur)))
        ;; Handle the case where the channel is closed and no matching output was found
        (do
          (destroy-tree proc)           ; Destroy the process
          (close! out-ch)               ; Close the output channel
          (throw (Exception. "Process completed without matching output")))))
    (assoc subprocess :response-ch response-ch)))

(defn cached-spawn-subprocess
  [options cache uid submission]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (llama-subprocess bin-dir model-path submission)))))

(defn create-complete-shell
  [{:keys [options]}]
  (let [cache (w/lru-cache-factory {})]
    (fn [user submission]
      (let [{:keys [uid]} (:vault user)
            cached? (w/lookup cache uid)
            proc (cached-spawn-subprocess options cache uid submission)
            proc (if (alive? (:proc proc))
                   proc
                   (do ;; not alive
                     (log/debug "llama::create-complete-shell:proc" "not alive?")
                     (w/evict cache uid)
                     (cached-spawn-subprocess options cache uid submission)))]
        (when cached?
          (go (>! (:in-ch proc) (str (as-role (last submission)) "\n"))))
        (<!! (:response-ch proc))
        #_(try
            (dh/with-timeout {:timeout-ms 8000}
              )
            (catch TimeoutExceededException _
              (fn [_] (:gateway-timeout errors))))))))

;; Todo make sure it works
;; - Emoji don't work with llama.clj
