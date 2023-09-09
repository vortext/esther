(ns vortext.esther.ai.llama
  (:require
   [diehard.core :as dh]
   [clojure.tools.logging :as log]
   [clojure.core.async :as async :refer
    [alts! timeout chan go-loop <! go >! <!! >!! close!]]
   [clojure.java.io :as io]
   [clj-commons.digest :as digest]
   [babashka.process :refer [process destroy-tree alive?]]
   [babashka.fs :as fs]
   [clojure.core.cache.wrapped :as w]
   [vortext.esther.util.json :as json]
   [vortext.esther.errors :refer [errors wrapped-error]]
   [clojure.string :as str])
  (:import [dev.failsafe TimeoutExceededException]))

(def end-of-turn "") ;; <|end_of_turn|>
(def prefix "User: ")

(def wait-for (* 1000 60 1)) ;; 1 minute

(defn generate-prompt-str
  [prompt]
  ;; Instructions
  (str (str/trim prompt) end-of-turn "\n\n"))

(defn shell-cmd
  [bin-dir model-path prompt]
  (let [prompt (generate-prompt-str prompt)
        cache-file (str "cache/" (digest/md5 prompt) ".bin")
        prompt-cache (fs/delete-on-exit (fs/canonicalize cache-file))
        gbnf (str (fs/canonicalize (io/resource "grammars/chat.gbnf")))

        tmp (str (fs/delete-on-exit (fs/create-temp-file)))
        model (str (fs/canonicalize (fs/path model-path)))]
    (spit tmp prompt)
    (str/join
     " "
     [(str (fs/real-path (fs/path bin-dir "main")))
      "-m" model
      "--grammar-file" gbnf
      ;; see https://github.com/ggerganov/llama.cpp/blob/master/docs/token_generation_performance_tips.md
      "--n-gpu-layers" 19
      "--threads" 32
      "--ctx-size" (* 2 2048)

      ;; Mirostat: A Neural Text Decoding Algorithm that Directly Controls Perplexity
      ;; https://arxiv.org/abs/2007.14966
      "--mirostat" 2
      "--mirostat-ent" 5
      "--mirostat-lr" 0.1

      ;; https://github.com/ggerganov/llama.cpp/tree/master/examples/main#context-management
      ;; Also see https://github.com/belladoreai/llama-tokenizer-js
      "--keep" -1
      "--prompt-cache" prompt-cache
      "-i"
      "--simple-io"
      "--interactive-first"
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
        out-ch (chan 128)
        in-ch (chan 128)
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

(defn extract-json-parse
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
            json-obj (extract-json-parse new-partial-json)]
        (if-not json-obj
          (recur new-partial-json)
          (if (:content json-obj)
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
          (log/debug "line" line)
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

(defn start-subprocess!
  [bin-dir model-path prompt]
  (let [response-ch (chan 32)
        partial-json-ch (chan 32)
        status-ch (chan)
        cmd (shell-cmd bin-dir model-path prompt)
        llama (llama-process status-ch cmd)]
    (when-let [subprocess
               (and llama
                    (-> llama
                        (handle-json-parsing partial-json-ch response-ch)
                        (handle-output-channel partial-json-ch status-ch)
                        (assoc :response-ch response-ch)))]
      (if (alive? (:proc subprocess))
        (when (= (<!! status-ch) :ready) subprocess)
        (do
          (close! partial-json-ch)
          (go (>! response-ch
                  (wrapped-error
                   :internal-server-error
                   (Exception. "llama:process-dead"))))
          {:proc nil
           :response-ch response-ch})))))

(defn cached-spawn-subprocess
  [options cache uid prompt]
  (let [{:keys [model-path bin-dir]} options]
    (w/lookup-or-miss
     cache uid
     (fn [_uid] (start-subprocess! bin-dir model-path prompt)))))

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
  [options cache user {:keys [:llm/submission :llm/prompt]}]
  (let [{:keys [uid]} (:vault user)
        running-proc? (checked-proc cache uid)
        proc (cached-spawn-subprocess options cache uid prompt)]
    (if-not proc
      (wrapped-error :uncaught-exception
                     (Exception. "internal-shell-complete-fn no proc"))
      (let [obj (if running-proc? (dissoc submission :context) submission)
            line (str prefix (json/write-value-as-string obj) end-of-turn "\n")]
        (log/debug "shell-complete-fn::complete" line)
        (go (>! (:in-ch proc) line))
        (let [response-ch (:response-ch proc)
              t (timeout wait-for)]
          (<!! (go
                 (let [[v ch] (alts! [response-ch t])]
                   (if (= ch t)
                     (throw (Exception. "Timeout")) v)))))))))


(defn shell-complete-fn
  [options cache]
  (fn [user submission]
    (try
      (dh/with-timeout {:timeout-ms wait-for}
        (internal-shell-complete-fn options cache user submission))
      (catch TimeoutExceededException e
        (do (log/warn "shell-complete-fn::timeout" e)
            ((:shutdown-fn (w/lookup cache (get-in user [:vault :uid]))))
            (wrapped-error :gateway-timeout e))))))

(defn shutdown-fn
  ([cache]
   (doseq [v (vals @cache)]
     (when-let [shutdown (:shutdown-fn v)]
       (shutdown))))
  ([cache uid]
   (when-let [shutdown (:shutdown-fn (checked-proc cache uid))]
     (shutdown))))

(defn create-interface
  [{:keys [options]}]
  (let [cache (w/lru-cache-factory {:threshold 1}) ;; yeah GPU mem will be an issue
        complete-fn (shell-complete-fn options cache)]
    {:shutdown-fn (partial shutdown-fn cache)
     :complete-fn complete-fn}))
