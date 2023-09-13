(ns vortext.esther.ai.llama
  (:require
    [babashka.fs :as fs]
    [babashka.process :refer [process destroy-tree alive?]]
    [clojure.core.async :as async :refer
     [alts! timeout chan go-loop <! go >! <!! >!! close!]]
    [clojure.core.cache.wrapped :as w]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [diehard.core :as dh]
    [vortext.esther.config :as config]
    [vortext.esther.util.json :as json]
    [vortext.esther.util.mustache :as mustache]
    [vortext.esther.util.zlib :as zlib])
  (:import
    (dev.failsafe
      TimeoutExceededException)))


(def wait-for (* 1000 60 1)) ; 1 minute

(def end-of-prompt "✔") ; Special char detected for booting

(def cache #(fs/path config/cache-dir %))


(defn flush-prompt!
  [{:keys [system-prefix system-suffix end-of-turn]}
   {:keys [:llm/prompt]}]
  (let [prompt-checksum (zlib/checksum prompt)
        prompt (str system-prefix
                    (str/trim prompt)
                    system-suffix
                    end-of-prompt
                    end-of-turn "\n\n")
        prompt-filename (format "prompt_%s.md" prompt-checksum)
        prompt-path (cache prompt-filename)
        _ (when-not (fs/exists? prompt-path)
            (spit (str prompt-path) prompt))]
    [prompt-path prompt-checksum]))


(defn flush-gbnf!
  [{:keys [grammar-template assistant-prefix assistant-suffix end-of-turn]}]
  (let [gbnf-template (str (fs/canonicalize (io/resource grammar-template)))
        grammar-checksum (zlib/crc32->base64-str
                           (zlib/calculate-crc32 gbnf-template))
        grammar-path (cache (format "grammar_%s.gbnf" grammar-checksum))
        _ (when-not (fs/exists? grammar-path)
            (spit (str grammar-path)
                  (mustache/render
                    (slurp gbnf-template)
                    {:assistant-prefix assistant-prefix
                     :assistant-suffix assistant-suffix
                     :end-of-turn end-of-turn})))]
    [grammar-path grammar-checksum]))


(defn- internal-config-obj
  [options {:keys [:llm/prompt-template] :as obj}]
  (let [{:keys [model-path bin-dir]} options
        [prompt-path _] (flush-prompt! options obj)
        [grammar-path _] (flush-gbnf! options)
        model-path (fs/canonicalize (fs/path model-path))
        cmd-path (fs/canonicalize (fs/path bin-dir "main"))
        cache-fingerprint (zlib/checksum (str model-path prompt-template))
        prompt-cache-path (cache (format "cache_%s.bin" cache-fingerprint))]
    {::prompt-path prompt-path
     ::grammar-path grammar-path
     ::model-path model-path
     ::cmd-path cmd-path
     ::prompt-cache-path prompt-cache-path}))


(defn shell-cmd
  [config]
  (str/join
    " "
    [(str (::cmd-path config))
     "-m" (str (::model-path config))
     "--grammar-file" (str (::grammar-path config))
     "--prompt-cache" (str (::prompt-cache-path config))
     "-f" (str (::prompt-path config))

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

     "-i"
     "--simple-io"
     "--interactive-first"]))


(defn send-sigint
  [pid]
  (let [cmd (str "kill -INT " pid)]
    (.exec (Runtime/getRuntime) cmd)))


(defn main-process
  [status-ch cmd]
  (let [_ (log/debug "llama::process-channels:cmd" cmd)
        proc (process {:shutdown destroy-tree
                       :err (java.io.OutputStream/nullOutputStream)}
                      cmd)

        sigint #(send-sigint (.pid (:proc proc)))
        out-ch (chan 128)
        in-ch (chan 128)
        rdr (io/reader (:out proc))
        shutdown-fn #(do
                       (log/warn "destroying" (:proc proc))
                       (go (>! status-ch :dead))
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


(defn handle-output-channel
  [subprocess partial-json-ch status-ch]
  (let [process-ready? (atom false)]
    (go-loop []
      (if-let [line (<! (:out-ch subprocess))]
        (do
          (log/debug "line" line)
          (when (and (not @process-ready?)
                     (str/includes? line end-of-prompt))
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


(defn extract-json-parse
  [output]
  (when (seq output)
    (let [start (str/index-of output "{")
          end (str/last-index-of output "}")]
      (when (and start end)
        (json/read-json-value (subs output start (inc end)))))))


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


(defn start-subprocess!
  [config _uid]
  (let [response-ch (chan 32)
        partial-json-ch (chan 32)
        status-ch (chan 1)
        main (main-process status-ch (shell-cmd config))]
    (when-let [subprocess
               (and main
                    (-> main
                        (handle-output-channel partial-json-ch status-ch)
                        (handle-json-parsing partial-json-ch response-ch)
                        (assoc :response-ch response-ch)))]
      (if (alive? (:proc subprocess))
        (when (= (<!! status-ch) :ready) subprocess)
        ;; Dead
        (do
          (close! partial-json-ch)
          (go (>! response-ch (Exception. "llama:process-dead")))
          {:proc nil
           :response-ch response-ch})))))


(defn cached-spawn-subprocess
  [cache uid config]
  (w/lookup-or-miss
    cache uid
    (fn [uid] (start-subprocess! config uid))))


(defn checked-proc
  [cache uid]
  (let [proc (w/lookup cache uid)
        java-proc (:proc proc)]
    (if (and java-proc (alive? java-proc))
      proc
      (when java-proc
        ((:shutdown-fn proc))
        (log/warn "process was dead")
        (w/evict cache uid)
        nil))))


(defn complete
  [options proc running? {:keys [:llm/submission]}]
  (let [{:keys [user-prefix user-suffix end-of-turn]} options
        write-line (if running? (dissoc submission :context) submission)
        json-line (json/write-value-as-string write-line)
        line (str user-prefix json-line user-suffix end-of-turn "\n")]
    (log/debug "complete:" write-line)
    (go (>! (:in-ch proc) line))
    (<!! (:response-ch proc))))


(defn start
  [options cache user obj]
  (let [{:keys [uid]} (:vault user)
        config (internal-config-obj options obj)]
    (cached-spawn-subprocess cache uid config)))


(defn- internal-shell-complete-fn
  [options cache user obj]
  (let [{:keys [uid]} (:vault user)
        running? (boolean (checked-proc cache uid))]
    (if-let [proc (start options cache user obj)]
      (complete options proc running? obj)
      (throw (Exception. "internal-shell-complete-fn no proc")))))


(defn shell-complete-fn
  [options cache]
  (fn [user obj]
    (try
      (dh/with-timeout {:timeout-ms wait-for}
                       (internal-shell-complete-fn options cache user obj))
      (catch TimeoutExceededException e
        (do (log/warn "shell-complete-fn::timeout" e)
            ((:shutdown-fn (w/lookup cache (get-in user [:vault :uid]))))
            (throw e))))))


(defn shutdown-fn
  ([cache]
   (doseq [[k v] @cache]
     (do (w/evict cache k)
         (when-let [shutdown (:shutdown-fn v)]
           (shutdown)))))
  ([cache uid]
   (when-let [shutdown (:shutdown-fn (checked-proc cache uid))]
     (w/evict cache uid)
     (shutdown))))


(defn create-instance
  [{:keys [options]}]
  (let [;; yeah GPU mem will be an issue
        cache (w/lru-cache-factory {:threshold 1})]
    {:_cache cache
     :shutdown-fn (partial shutdown-fn cache)
     :complete-fn (shell-complete-fn options cache)}))
