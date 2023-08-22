(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.config :refer [errors]]
   [clojure.core.async :as async :refer [chan go-loop <! go >! <!! >!! close!]]
   [clojure.java.io :as io]
   [babashka.process :refer [process destroy-tree alive?]]
   [clojure.core.cache.wrapped :as w]
   [babashka.fs :as fs]
   [vortext.esther.util :refer
    [parse-maybe-json escape-newlines]]
   [clojure.string :as str]))


(defn as-role
  [entry]
  (str (if (= (:role entry) "assistant") "Esther:" "User:")
       (:content entry)))

(defn generate-prompt-str
  [submission]
  (str
   (:content (first submission)) ;; Instructions
   (str/join
    "\n"
    (for [entry (rest submission)]
      (as-role entry)))
   "\n\n"))

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
        ;;tmp (str (fs/delete-on-exit (fs/create-temp-file)))

        tmp (str (fs/create-temp-file))
        pty-bridge (str (fs/canonicalize (io/resource "scripts/pty_bridge.py")))
        model (str (fs/canonicalize (fs/path model-path)))]
    (spit tmp prompt)
    (str
     "python " pty-bridge " '"
     (str/join
      " "
      [(str (fs/real-path (fs/path bin-dir "main")))
       "-m" model
       "-i"
       "--simple-io"
       "-r" "User:"
       "-eps" "1e-5" ;; for best generation quality LLaMA 2
       ;;"-gqa" "8"    ;; for 70B models to work
       "--ctx-size" 2048
       "--threads" (.availableProcessors (Runtime/getRuntime))
       "-f" (str tmp)])
     "'")))

(defn process-channels
  [cmd]
  (let [_ (log/debug "llama::process-channels:cmd" cmd)
        proc (process {:shutdown destroy-tree
                       :err (java.io.OutputStream/nullOutputStream)}
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
    (let [sb (StringBuilder.)]
      (go-loop []
        (let [char (char (.read rdr))]
          (if (= char \newline)
            (do
              (>!! out-ch (str sb))
              (.setLength sb 0))
            (.append sb char))
          (recur))))
    {:proc proc
     :in-ch in-ch
     :out-ch out-ch}))

(defn llama-subprocess
  [bin-dir model-path submission]
  (let [subprocess (process-channels
                    (process-cmd bin-dir model-path submission))
        {:keys [proc out-ch in-ch]} subprocess
        response-ch (chan 32)
        last-entry (:content (last submission))
        stop #(go (>! in-ch "[[STOP]]"))
        seen-last-entry? (atom false)
        partial-json (atom "")
        status-ch (chan 8)]
    (go-loop []
      (if-let [line (<! out-ch)]
        (do (when (str/includes? line last-entry)
              (>! status-ch :seen-last-entry)
              (reset! seen-last-entry? true))
            (when @seen-last-entry?
              (swap! partial-json str line)
              (when-let [json-obj (safe-parse @partial-json)]
                (when (:response json-obj)
                  (stop)
                  (>! response-ch json-obj))
                (reset! partial-json "")))
            (recur))
        ;; Handle the case where the channel is closed and no matching output was found
        (do
          (destroy-tree proc)           ; Destroy the process
          (close! out-ch)               ; Close the output channel
          (throw (Exception. "Process completed without matching output")))))
    (when (= (<!! status-ch) :seen-last-entry)
      (assoc subprocess
             :response-ch response-ch))))



(defn cached-spawn-subprocess
  [options cache uid submission]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (llama-subprocess bin-dir model-path submission)))))

(defn llama-shell-complete-fn
  [options cache]
  (fn [user submission]
    (let [{:keys [uid]} (:vault user)
          running-proc? (when-let [proc (w/lookup cache uid)]
                          (alive? (:proc proc)))
          _ (when-not running-proc? (w/evict cache uid))
          proc (cached-spawn-subprocess options cache uid submission)]
      (when running-proc?
        (let [entry (last submission)]
          (go (>! (:in-ch proc) (as-role entry)))))
      (<!! (:response-ch proc)))))

(defn create-complete-shell
  [{:keys [options]}]
  (let [cache (w/lru-cache-factory {:threshold 32})]
    (llama-shell-complete-fn options cache)))
