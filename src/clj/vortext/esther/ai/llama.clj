(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer [chan go-loop <! go >! <!! >!! close!]]
   [clojure.java.io :as io]
   [babashka.process :refer [process destroy-tree alive?]]
   [clojure.core.cache.wrapped :as w]
   [babashka.fs :as fs]
   [vortext.esther.util :refer [read-json-value escape-json]]
   [vortext.esther.ai.llama-jna :as llama]
   [vortext.esther.config :refer [errors]]
   [clojure.string :as str]))

(def ai-name "Esther")
(def end-of-turn "<|end_of_turn|>")

(defn num-tokens
  [model-path text]
  (let [ctx (llama/create-context model-path {})
        add-bos? true
        [num-tokens _token-buf] (llama/tokenize ctx text add-bos?)]
    (.close ctx)
    num-tokens))

(defn as-role
  [entry]
  (str (if (= (:role entry) "assistant") (str ai-name ": ") "User: ")
       (:content entry)))

(defn generate-prompt-str
  [submission]
  (str
   (str (str/trim (:content (first submission))) end-of-turn "\n") ;; Instructions
   (str/join
    (str end-of-turn "\n")
    (for [entry (rest submission)]
      (str (as-role entry))))
   (str end-of-turn "\n\n")))

(defn shell-cmd
  [bin-dir model-path submission]
  (let [prompt (generate-prompt-str submission)
        _ (log/info prompt)
        instructions (:content (first submission))
        keep-n-tokens (num-tokens model-path instructions)
        gbnf (str (fs/canonicalize (io/resource "grammars/json-chat.gbnf")))

        tmp (str (fs/delete-on-exit (fs/create-temp-file)))
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
       "-eps" "1e-5" ;; for best generation quality LLaMA 2 (doesn't work anymore with guff?)
       "--ctx-size" 2048
       ;; https://github.com/ggerganov/llama.cpp/tree/master/examples/main#context-management
       ;; Also see https://github.com/belladoreai/llama-tokenizer-js
       "--keep" keep-n-tokens
       "-i"
       "--simple-io" ;; required for pty-bridge?
       "-r" "User:"
       ;;"-gqa" "8"    ;; for 70B models to work
       "--threads" (max 32 (/ 2  (.availableProcessors (Runtime/getRuntime))))
       "-f" (str tmp)])
     "'")))

(defn pty-bridge-process
  [status-ch cmd]
  (let [_ (log/debug "llama::process-channels:cmd" cmd)
        proc (process {:shutdown destroy-tree
                       :err (java.io.OutputStream/nullOutputStream)}
                      cmd)
        out-ch (chan 32)
        in-ch (chan 32)
        sigint #(go (>! in-ch "[[STOP]]"))
        rdr (io/reader (:out proc))
        shutdown-fn #(do
                       (log/warn "destroying" (:proc proc))
                       (go (>! status-ch :failed))
                       (destroy-tree proc)
                       (close! out-ch)
                       (close! in-ch))
        sb (StringBuilder.)]
    (go
      (with-open [wrtr (io/writer (:in proc))]
        (loop []
          (when-let [line (<! in-ch)]
            (.write wrtr (str line "\n"))
            (.flush wrtr)
            (recur)))))
    (go-loop []
      (let [code-point (.read rdr)]
        (if (not= code-point -1)
          (let [char (char code-point)]
            (if (= char \newline)
              (let [line (str sb)]
                (>!! out-ch line)
                (.setLength sb 0))
              (.append sb char))
            (recur))
          (shutdown-fn))))
    {:proc proc
     :in-ch in-ch
     :out-ch out-ch
     :sigint sigint
     :shutdown-fn shutdown-fn}))

(defn safe-parse
  [output]
  (try
    (when (seq output)
      (let [start (str/index-of output "{")
            end (str/last-index-of output "}")]
        (when (and start end)
          (read-json-value (escape-json
                            (subs output start (inc end)))))))
    (catch com.fasterxml.jackson.core.JsonParseException e (log/warn e))))

(defn handle-json-parsing [subprocess partial-json-ch response-ch]
  (go-loop [partial-json ""]
    (if-let [line (<! partial-json-ch)]
      (let [new-partial-json (str partial-json line)
            json-obj (safe-parse new-partial-json)]
        (if-not json-obj
          (recur new-partial-json)
          (if (:response json-obj)
            (do
              (>! response-ch json-obj)
              ((:sigint subprocess))
              (recur ""))
            (recur ""))))
      ((:sigint subprocess))))
  subprocess)

(defn handle-output-channel
  [subprocess last-entry process-ready? partial-json-ch status-ch]
  (go-loop []
    (if-let [line (<! (:out-ch subprocess))]
      (do
        (log/info "llama:line" line)
        (when (str/includes? line last-entry)
          (>! status-ch :ready)
          (reset! process-ready? true))
        (when @process-ready?
          (>! partial-json-ch line))
        (recur))
      (do
        ((:shutdown-fn subprocess))
        (close! partial-json-ch)
        (go (>! status-ch :stream-closed)))))
  subprocess)

(defn start-subprocess! [bin-dir model-path submission]
  (let [last-entry (:content (last submission))
        process-ready? (atom false)
        response-ch (chan 32)
        partial-json-ch (chan 32)
        status-ch (chan 32)
        pty (pty-bridge-process status-ch (shell-cmd bin-dir model-path submission))]
    (when-let [subprocess
               (and pty
                    (-> pty
                        (handle-json-parsing partial-json-ch response-ch)
                        (handle-output-channel last-entry process-ready? partial-json-ch status-ch)
                        (assoc :response-ch response-ch)))]
      (if (alive? (:proc subprocess))
        (when (= (<!! status-ch) :ready) subprocess)
        subprocess))))

(defn cached-spawn-subprocess
  [options cache uid submission]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (start-subprocess! bin-dir model-path submission)))))

(defn checked-proc?
  [cache uid]
  (let [proc (w/lookup cache uid)
        java-proc (:proc proc)]
    (if (and java-proc (alive? java-proc))
      proc
      (when java-proc
        (destroy-tree java-proc)
        (log/debug "process was dead")
        (w/evict cache uid)
        nil))))

(defn shell-complete-fn
  [options cache]
  (fn [user submission]
    (let [{:keys [uid]} (:vault user)
          running-proc? (checked-proc? cache uid)
          proc (cached-spawn-subprocess options cache uid submission)]
      (if-not proc
        (:uncaught-exception errors)
        (try
          (when running-proc?
            (let [entry (last submission)
                  line (str (as-role entry) end-of-turn "\n")]
              (log/debug "running-proc? yes." "sending" line)
              (go (>! (:in-ch proc) line))))
          (<!! (:response-ch proc))
          (catch Exception _e (:uncaught-exception errors)))))))

(defn shutdown-everything
  [cache]
  (doseq [v (vals @cache)]
    (when-let [shutdown (:shutdown-fn v)]
      (log/warn "shutting down" (shutdown)))))

(defn create-complete-shell
  [{:keys [options]}]
  (let [cache (w/lru-cache-factory {:threshold 32})
        complete-fn (shell-complete-fn options cache)]
    {:shutdown-fn #(shutdown-everything cache)
     :complete-fn complete-fn}))
