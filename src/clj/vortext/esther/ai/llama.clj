(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer [chan go-loop <! go >! <!! >!! close!]]
   [clojure.java.io :as io]
   [babashka.process :refer [process destroy-tree alive?]]
   [clojure.core.cache.wrapped :as w]
   [babashka.fs :as fs]
   [vortext.esther.util :refer [read-json-value escape-newlines]]
   [clojure.string :as str]))

(def ai-name "Esther")

(defn as-role
  [entry]
  (str (if (= (:role entry) "assistant") (str ai-name ":") "User:")
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

(defn process-cmd
  [bin-dir model-path submission]
  (let [prompt (generate-prompt-str submission)
        ;;tmp (str (fs/delete-on-exit (fs/create-temp-file)))

        gbnf (str (fs/canonicalize (io/resource "grammars/json-chat.gbnf")))

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
       "--grammar-file" gbnf
       ;; see https://github.com/ggerganov/llama.cpp/blob/master/docs/token_generation_performance_tips.md
       "--n-gpu-layers" 20
       "-eps" "1e-5" ;; for best generation quality LLaMA 2
       "--ctx-size" 2048
       "-i"
       "--simple-io" ;; required
       "-r" "User:"
       ;;"-gqa" "8"    ;; for 70B models to work
       "--threads" (max 32 (/ 2  (.availableProcessors (Runtime/getRuntime))))
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
        sigint #(go (>! in-ch "[[STOP]]"))
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
            (let [line (str sb)]
              (>!! out-ch line)
              (.setLength sb 0))
            (.append sb char))
          (recur))))
    {:proc proc
     :in-ch in-ch
     :out-ch out-ch
     :sigint sigint
     :destroy #(((:shutdown proc)) (close! out-ch) (close! in-ch))}))

(defn safe-parse
  [output]
  (try
    (when (seq output)
      (let [start (str/index-of output "{")
            end (str/last-index-of output "}")]
        (when (and start end)
          (read-json-value (escape-newlines
                            (subs output start (inc end)))))))
    (catch com.fasterxml.jackson.core.JsonParseException e (log/warn e))))

(defn handle-json-parsing [subprocess partial-json-ch response-ch]
  (go-loop [partial-json ""]
    (if-let [line (<! partial-json-ch)]
      (let [new-partial-json (str partial-json line)
            json-obj (safe-parse new-partial-json)]
        (log/debug "JSON object" json-obj)
        (if-not json-obj
          (recur new-partial-json)
          (if (:response json-obj)
            (do
              (>! response-ch json-obj)
              ((:sigint subprocess))
              (recur ""))
            (recur ""))))
      ((:sigint subprocess)))))

(defn handle-output-channel
  [subprocess last-entry process-ready? partial-json-ch status-ch]
  (go-loop []
    (if-let [line (<! (:out-ch subprocess))]
      (do
        (log/debug "Read line:" line)
        (when (str/includes? line last-entry)
          (log/debug "Seen last entry")
          (>! status-ch :started?)
          (reset! process-ready? true))
        (when @process-ready?
          (>! partial-json-ch line))
        (recur))
      (do
        ((:destroy subprocess))
        (close! partial-json-ch)
        (close! status-ch)
        (throw (Exception. "Process completed without matching output"))))))

(defn subprocess [bin-dir model-path submission]
  (let [subprocess (process-channels (process-cmd bin-dir model-path submission))
        last-entry (:content (last submission))
        process-ready? (atom false)
        response-ch (chan 32)
        partial-json-ch (chan 32)
        status-ch (chan 32)]

    (handle-json-parsing subprocess partial-json-ch response-ch)
    (handle-output-channel subprocess last-entry process-ready? partial-json-ch status-ch)

    (when (= (<!! status-ch) :started?) ;; Todo failed condition
      (assoc subprocess :response-ch response-ch))))


(defn cached-spawn-subprocess
  [options cache uid submission]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (subprocess bin-dir model-path submission)))))

(defn checked-proc?
  [cache uid]
  (let [proc (w/lookup cache uid)
        java-proc (:proc proc)]
    (if (and java-proc (alive? java-proc))
      true
      (when java-proc
        (destroy-tree java-proc)
        (log/debug "process was dead")
        (w/evict cache uid)
        false))))

(defn llama-shell-complete-fn
  [options cache]
  (fn [user submission]
    (let [{:keys [uid]} (:vault user)
          running-proc? (checked-proc? cache uid)
          proc (cached-spawn-subprocess options cache uid submission)]
      (when running-proc?
        (let [entry (last submission)]
          (log/debug "running-proc? yes" (:proc proc) entry)
          (go (>! (:in-ch proc) (as-role entry)))))
      (<!! (:response-ch proc)))))

(defn create-complete-shell
  [{:keys [options]}]
  (let [cache (w/lru-cache-factory {:threshold 32})]
    (llama-shell-complete-fn options cache)))
