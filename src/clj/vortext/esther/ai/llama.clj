(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
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
   "\n"))

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
        model (str (fs/real-path (fs/path model-path)))]
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
            (if (= line "[[CTRL-C]]")   ; Special trigger for Ctrl+C
              (.write wrtr "[[CTRL-C]]")
              (.write wrtr (str line "\n")))
            (.flush wrtr)
            (recur)))))
    (let [sb (StringBuilder.)]
      (go-loop []
        (let [char (char (.read rdr))]
          (if (= char \newline)
            (do
              (log/debug  (str sb))
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
        seen-last-entry? (atom false)
        status-ch (chan 8)]
    (go-loop []
      (if-let [line (<! out-ch)]
        (do
          (when (str/includes? line last-entry)
            (go (>! status-ch :seen-last-entry))
            (reset! seen-last-entry? true))
          (if-let [json-obj (and @seen-last-entry? (safe-parse line))]
            (if (:response json-obj)
              (do
                (>! in-ch "[[CTRL-C]]")
                (>! response-ch json-obj)
                (recur))
              (recur))
            (recur)))
        ;; Handle the case where the channel is closed and no matching output was found
        (do
          (destroy-tree proc)           ; Destroy the process
          (close! out-ch)               ; Close the output channel
          (throw (Exception. "Process completed without matching output")))))
    (when (= (<!! status-ch) :seen-last-entry)
      (assoc subprocess
             :status-ch status-ch
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

;; Todo make sure it works
;; - Emoji don't work with llama.clj

(comment
  (def fast-in-terminal "python /array/Sync/projects/esther/resources/scripts/pty_bridge.py \"/array/Sync/projects/esther/native/llama.cpp/build/bin/main -m /array/Models/TheBloke/llama-2-7b-chat.ggmlv3.q4_K_M.bin -i --simple-io -r User: -eps 1e-5 --ctx-size 2048 --threads 48 -f /tmp/02f1ea1f-84d8-4a6e-a146-394d169924fa15377515098983504690f2ba3d4d-14bc-480d-b8b2-bb4b4cdac524\"")

  (process {:out :inherit} fast-in-terminal))
