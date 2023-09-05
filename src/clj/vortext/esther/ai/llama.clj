(ns vortext.esther.ai.llama
  (:require
   [diehard.core :as dh]
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer [chan go-loop <! go >! <!! >!! close!]]
   [clojure.java.io :as io]
   [clj-commons.digest :as digest]
   [babashka.process :refer [process destroy-tree alive?]]
   [babashka.fs :as fs]
   [clojure.core.cache.wrapped :as w]
   [vortext.esther.util.json :as json]
   [vortext.esther.config :refer [errors]]
   [clojure.string :as str])
  (:import [dev.failsafe TimeoutExceededException]))

(def end-of-turn "<|end_of_turn|>")

(defn generate-prompt-str
  [submission]
  ;; Instructions
  (str (str/trim (:content (first submission))) end-of-turn "\n\n"))

(defn shell-cmd
  [bin-dir model-path submission]
  (let [prompt (generate-prompt-str submission)
        cache-file (str "cache/" (digest/md5 prompt) ".bin")
        prompt-cache (fs/canonicalize cache-file)
        gbnf (str (fs/canonicalize (io/resource "grammars/json-chat.gbnf")))

        tmp (str (fs/delete-on-exit (fs/create-temp-file)))
        model (str (fs/canonicalize (fs/path model-path)))]
    (spit tmp prompt)
    (str/join
     " "
     [(str (fs/real-path (fs/path bin-dir "main")))
      "-m" model
      "--grammar-file" gbnf
      ;; see https://github.com/ggerganov/llama.cpp/blob/master/docs/token_generation_performance_tips.md
      "--n-gpu-layers" 20
      "--ctx-size" (* 2 2084)
      ;; https://github.com/ggerganov/llama.cpp/tree/master/examples/main#context-management
      ;; Also see https://github.com/belladoreai/llama-tokenizer-js
      "--keep" -1
      "--prompt-cache" prompt-cache
      "-i"
      "--simple-io"
      "--interactive-first"
      "--threads" (max 32 (/ 2  (.availableProcessors (Runtime/getRuntime))))
      "-f" (str tmp)])))

(defn send-sigint [pid]
  (let [cmd (str "kill -INT " pid)]
    (.exec (Runtime/getRuntime) cmd)))

(defn llama-process
  [status-ch cmd]
  (let [_ (log/debug "llama::process-channels:cmd" cmd)
        proc (process {:shutdown destroy-tree
                       :err (java.io.OutputStream/nullOutputStream)}
                      cmd)
        out-ch (chan 32)
        in-ch (chan 32)
        sigint #(send-sigint (.pid (:proc proc)))
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
          (json/read-json-value (subs output start (inc end))))))
    (catch com.fasterxml.jackson.core.JsonParseException e (log/warn e))))

(defn handle-json-parsing
  [subprocess partial-json-ch response-ch]
  (go-loop [partial-json ""]
    (if-let [line (<! partial-json-ch)]
      (let [new-partial-json (str partial-json "\\n" line)
            json-obj (safe-parse new-partial-json)]
        (if-not json-obj
          (recur new-partial-json)
          (if (:reply json-obj)
            (do
              (>! response-ch json-obj)
              ((:sigint subprocess))
              (recur ""))
            (recur ""))))
      ((:sigint subprocess))))
  subprocess)

(defn handle-output-channel
  [subprocess partial-json-ch status-ch]
  (let [process-ready? (atom false)]
    (go-loop []
      (if-let [line (<! (:out-ch subprocess))]
        (do
          (log/info "llama:line" line)
          (when (and (not @process-ready?)
                     (str/includes? line end-of-turn))
            (>! status-ch :ready)
            (reset! process-ready? true))
          (when @process-ready?
            (>! partial-json-ch line))
          (recur))
        (do
          ((:shutdown-fn subprocess))
          (close! partial-json-ch)
          (go (>! status-ch :stream-closed))))))
  subprocess)

(defn start-subprocess! [bin-dir model-path submission]
  (let [response-ch (chan 32)
        partial-json-ch (chan 32)
        status-ch (chan)
        llama (llama-process status-ch (shell-cmd bin-dir model-path submission))]
    (when-let [subprocess
               (and llama
                    (-> llama
                        (handle-json-parsing partial-json-ch response-ch)
                        (handle-output-channel partial-json-ch status-ch)
                        (assoc :response-ch response-ch)))]
      (when (alive? (:proc subprocess))
        (when (= (<!! status-ch) :ready) subprocess)))))

(defn cached-spawn-subprocess
  [options cache uid submission]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (start-subprocess! bin-dir model-path submission)))))

(defn checked-proc
  [cache uid]
  (let [proc (w/lookup cache uid)
        java-proc (:proc proc)]
    (if (and java-proc (alive? java-proc))
      proc
      (when java-proc
        ((:shutdown-fn proc))
        (log/debug "process was dead")
        (w/evict cache uid)
        nil))))

(defn- internal-shell-complete-fn
  [options cache user submission]
  (let [{:keys [uid]} (:vault user)
        running-proc? (checked-proc cache uid)
        proc (cached-spawn-subprocess options cache uid submission)]
    (if-not proc
      (:uncaught-exception errors)
      (try
        (let [entry (:content (last submission))
              obj (if-not running-proc?
                    entry (-> entry (dissoc :context)))
              line (str "User: " (json/write-value-as-string obj) end-of-turn "\n")]
          (log/debug "shell-complete-fn::complete" line)
          (go (>! (:in-ch proc) line)))
        (<!! (:response-ch proc))
        (catch Exception _e (:uncaught-exception errors))))))

(defn shell-complete-fn
  [options cache]
  (fn [user submission]
    (try
      (dh/with-timeout {:timeout-ms 120000}
        (internal-shell-complete-fn options cache user submission))
      (catch TimeoutExceededException e
        (do (log/warn "shell-complete-fn::timeout" e)
            ((:shutdown-fn (w/lookup cache (get-in user [:vault :uid]))))
            (:gateway-timeout errors))))))

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
