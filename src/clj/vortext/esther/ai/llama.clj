;; Adapted from https://github.com/phronmophobic/llama.clj

;; The MIT License (MIT)

;; Copyright © 2023 Adrian Smith

;; Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
;; documentation files (the “Software”), to deal in the Software without restriction, including without limitation the
;; rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
;; permit persons to whom the Software is furnished to do so, subject to the following conditions:

;; The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
;; Software.

;; THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
;; WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
;; COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
;; OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

(ns vortext.esther.ai.llama
  (:refer-clojure :exclude [remove printf])  ;; [WARNING]
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.phronemophobic.clong.gen.jna :as gen]
   [vortext.esther.ai.grammar :as grammar]
   [vortext.esther.util.jna
    :refer [->bool seq->memory ->float-array-by-reference]])
  (:import
   java.lang.ref.Cleaner
   (com.sun.jna Memory Pointer Structure)
   (com.sun.jna.ptr ByteByReference FloatByReference IntByReference)
   (java.nio ByteBuffer CharBuffer)
   (java.nio.charset Charset CodingErrorAction)))


(def ^:no-doc library
  (com.sun.jna.NativeLibrary/getInstance
   "llama"
   {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"}))

(def api (with-open [rdr (io/reader (io/resource "api/llama.edn"))
                     rdr (java.io.PushbackReader. rdr)]
           (edn/read rdr)))

(gen/def-api library api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))

(import-structs!)

(def ^:dynamic
  *num-threads*
  "Number of threads used when generating tokens."
  (.. Runtime getRuntime availableProcessors))


(defonce cleaner (delay (Cleaner/create)))

(def ^:private token-data-size (.size (llama_token_data.)))


(defonce ^:private llm-init
  (delay
    (llama_backend_init 0)))


(defn eos
  "Returns the llama end of sentence token."
  ;; only for backwards compatibility
  [ctx]
  (llama_token_eos ctx))


(defn bos
  "Returns the llama beginning of sentence token."
  ;; only for backwards compatibility
  [ctx]
  (llama_token_bos ctx))


(defn nl
  "Returns the llama next line token."
  [ctx]
  (llama_token_nl ctx))


(defn eot
  "Returns the llama end of turn token."
  [ctx]
  (llama_token_eot ctx))


(defn ^:private map->llama-context-params
  [m]
  (reduce-kv
   (fn [^llama_context_params params k v]
     (case k
       :seed (.writeField params "seed" (int v))
       :n-ctx (.writeField params "n_ctx" (int v))
       :n-batch (.writeField params "n_batch" (int v))
       :n-threads (.writeField params "n_threads" (int v))
       :n-threads-batch (.writeField params "n_threads_batch" (int v))

       :rope-freq-base (.writeField params "rope_freq_base" (float v))
       :rope-freq-scale (.writeField params "rope_freq_scale" (float v))

       :mul_mat_q (.writeField params "mul_mat_q" (->bool v))
       :f16-kv (.writeField params "f16_kv" (->bool v))
       :logits-all (.writeField params "logits_all" (->bool v))
       :embedding (.writeField params "embedding" (->bool v))
       ;; default
       nil)
     ;; return params
     params)
   (llama_context_default_params)
   m))


(defn ^:private map->llama-model-params
  [m]
  (reduce-kv
   (fn [^llama_model_params params k v]
     (case k
       :n-gpu-layers (.writeField params "n_gpu_layers" (int v))
       :main-gpu (.writeField params "main_gpu" (int v))
       :tensor-split (.writeField params "tensor_split" (->float-array-by-reference  v))
       ;; :progress-callback (.writeField params "progress_callback" v)
       ;; :progress-callback-user-data (.writeField params "progress_callback_user_data" v)
       :vocab-only (.writeField params "vocab_only" (->bool v))
       :use-mmap (.writeField params "use_mmap" (->bool v))
       :use-mlock (.writeField params "use_mlock" (->bool v))
       ;; default
       nil)
     ;; return params
     params)
   (llama_model_default_params)
   m))


(defn create-context
  "Create and return an opaque llama context.

  `model-path` should be an absolute or relative path to a F16, Q4_0, Q4_1, Q5_0, Q5_1, or Q8_0 ggml model.

  Resources can be freed by calling .close on the returned context.
  Using a closed context is undefined and will probably crash the JVM.

  Contexts are not thread-safe. Using the same context on multiple threads
  is undefined and will probably crash the JVM.
  "
  ([model-path]
   (create-context model-path nil))
  ([model-path params]
   @llm-init
   (let [^llama_context_params
         llama-context-params (map->llama-context-params params)

         ^llama_model_params
         llama-model-params (map->llama-model-params params)

         model
         (llama_load_model_from_file model-path llama-model-params)

         _ (when (nil? model)
             (throw (ex-info "Error creating model"
                             {:params params
                              :model-path model-path})))
         context (llama_new_context_with_model model llama-context-params)

         ctx-ptr (atom (Pointer/nativeValue context))
         model-ptr (atom (Pointer/nativeValue model))

         model-ref (atom model)
         ;; idempotent cleanup of context
         ;; must not hold references to context!
         delete-context (fn []
                          (let [[old _new] (swap-vals! ctx-ptr (constantly nil))]
                            (when old
                              (llama_free (Pointer. old))
                              ;; make sure model doesn't lose
                              ;; all references and get garbage
                              ;; collected until context is freed.
                              (reset! model-ref nil))))
         ;; idempotent cleanup of model
         ;; must not hold references to model!
         delete-model (fn []
                        (let [[old _new] (swap-vals! model-ptr (constantly nil))]
                          (when old
                            (llama_free_model (Pointer. old)))))

         n-batch (.readField llama-context-params "n_batch")
         n-ctx (llama_n_ctx context)
         n-vocab (llama_n_vocab model)

         ;; make context autocloseable and implement
         ;; some map lookup interfaces
         context (proxy [Pointer
                         clojure.lang.ILookup
                         java.lang.AutoCloseable]
                     [(Pointer/nativeValue context)]
                   (valAt
                     [k]
                     (case k
                       :n-ctx n-ctx
                       :n-batch n-batch
                       :n-vocab n-vocab
                       :model @model-ref
                       ;; else
                       nil))

                   (close
                     []
                     (delete-context)
                     (delete-model)))]

     ;; cleanup
     (.register ^Cleaner @cleaner context delete-context)
     (.register ^Cleaner @cleaner model delete-model)
     context)))


(defn ^:private llama-token-to-str
  [ctx token]
  (let [buffer-size (* 8 Character/BYTES)
        ^ByteBuffer buffer (ByteBuffer/allocate buffer-size)
        n-tokens (llama_token_to_piece (:model ctx) token (.array buffer) buffer-size)]
    (if (< n-tokens 0)
      (let [actual-size (Math/abs (int n-tokens))
            resized-buffer (ByteBuffer/allocate actual-size)]
        (let [check (llama_token_to_piece (:model ctx) token (.array resized-buffer) actual-size)]
          (assert (= check (- n-tokens)) "Mismatch in expected size from llama_token_to_piece")
          [actual-size resized-buffer]))
      [n-tokens buffer])))


(defn decode-token-to-char
  ([ctx]
   (decode-token-to-char ctx (Charset/forName "UTF-8")))
  ([ctx ^Charset charset]
   (fn [rf]
     (let [decoder (.newDecoder charset)
           input-buffer (ByteBuffer/allocate 256)
           output-buffer (CharBuffer/allocate 256)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result token]
          (let [[len ^ByteBuffer result-buf] (llama-token-to-str ctx token)]
            (.put input-buffer (.array result-buf) 0 len)
            (.flip input-buffer) ; Preparing buffer for read
            (let [decoder-result (.decode decoder input-buffer output-buffer false)]
              (cond
                (.isUnderflow decoder-result)
                (do
                  (.compact input-buffer) ; Preparing buffer for write
                  (.flip output-buffer)   ; Preparing buffer for read
                  (let [result (reduce rf result output-buffer)]
                    (.clear output-buffer)
                    result))
                (.isOverflow decoder-result)
                (throw (ex-info "Decoder buffer too small" {}))
                (.isError decoder-result)
                (throw (ex-info "Decoder Error" {:decoder decoder}))
                :else
                (throw (Exception. "Unexpected decoder state.")))))))))))


(defn ^:private char->str
  "Transducer that expects a stream of chars. If a surrogate pair is detected,
  wait until the full pair is available before emitting."
  []
  (fn [rf]
    (let [v (volatile! nil)]
      (fn
        ([] (rf))
        ([result]
         (let [result (if-let [c @v]
                        (unreduced (rf result c))
                        result)]
           (rf result)))
        ([result c]
         (if-let [c1 @v]
           (do
             (vreset! v nil)
             (rf result (str c1 c)))
           (if (Character/isHighSurrogate c)
             (do
               (vreset! v c)
               result)
             (rf result (str c)))))))))


(defn ^:private decode-token-to-str
  "Returns a transducer that expects a stream of llama tokens
  and outputs a stream of strings.

  The transducer will buffer intermediate results until enough
  bytes to decode a character are available. Also combines
  surrogate pairs of characters."
  ([ctx]
   (decode-token-to-str ctx (Charset/forName "UTF-8")))
  ([ctx ^Charset charset]
   (comp
     (decode-token-to-char ctx charset)
     (char->str))))


;;
;; Tokenize
;;
(defn untokenize
  "Given a sequence of tokens, return the string representation."
  [ctx tokens]
  (str/join
    (eduction
      (decode-token-to-str ctx)
      tokens)))


(defn tokenize
  [ctx s add-bos?]
  (let [add-bos (->bool add-bos?)
        special (->bool true)
        s (if add-bos? (str " " s) s)
        max-tokens (+ add-bos (alength (.getBytes ^String s "utf-8")))
        token-buf* (doto (Memory. (* max-tokens Integer/BYTES)) (.clear))
        num-tokens (llama_tokenize
                    (:model ctx) s
                    (count s) token-buf* max-tokens add-bos special)]
    [num-tokens (vec (.getIntArray token-buf* 0 num-tokens))]))


;;
;; Samplers
;;
(defn get-logits
  "Returns a copy of the current context's logits as a float array."
  ([ctx] (get-logits ctx 0))
  ([ctx i]
   ^floats (-> ^FloatByReference (llama_get_logits_ith ctx i)
               .getPointer
               (.getFloatArray 0 (:n-vocab ctx)))))


(defn ^:private logits->candidates
  [logits n ^Memory candidates-buf*]
  (doseq [id (range n)]
    (let [base-addr (* id token-data-size)
          logit (aget ^floats logits (int id))]
      (.setInt candidates-buf* base-addr id)
      (.setFloat candidates-buf* (+ base-addr 4) logit)
      (.setFloat candidates-buf* (+ base-addr 8) 0)))
  (let [candidates-array-head
        (doto (Structure/newInstance llama_token_dataByReference candidates-buf*) (.read))

        candidates*
        (doto (llama_token_data_arrayByReference.)
          (.writeField "data" candidates-array-head)
          (.writeField "size" (long n))
          (.writeField "sorted" (byte 0)))]
    candidates*))


(defn sample-mirostat-v2
  [ctx logits candidates-buf* mu* tau eta temp]
  (let [mu (FloatByReference. @mu*)
        candidates (logits->candidates logits (:n-vocab ctx) candidates-buf*)
        _ (llama_sample_temp ctx candidates temp)
        next-token (llama_sample_token_mirostat_v2 ctx candidates tau eta mu)]
    (vreset! mu* (.getValue mu))
    next-token))


(defn init-mirostat-v2-sampler
  "Given a context, returns a sampling function that uses the llama.cpp mirostat_v2 implementation."
  ([ctx]
   (let [tau (float 5.0)
         eta (float 0.1)
         temp (float 0.8)]
     (init-mirostat-v2-sampler ctx tau eta temp)))
  ([ctx tau eta temp]
   (let [candidates-buf* (doto (Memory. (* token-data-size (:n-vocab ctx))) (.clear))]
     (fn [logits reset?]
       (sample-mirostat-v2
        ctx
        logits
        candidates-buf*
        (volatile! (* 2 tau))
        tau
        eta
        temp)))))


(defn init-grammar-sampler
  ([ctx grammar-str] (init-grammar-sampler ctx grammar-str {}))
  ([ctx grammar-str params]
   (let [params (grammar/map->llama-sampler-params params)
         grammar* (atom nil)
         n-vocab (:n-vocab ctx)
         candidates-buf* (doto (Memory. (* token-data-size n-vocab)) (.clear))]
     (fn [logits reset?]
       (let [candidates (logits->candidates logits n-vocab candidates-buf*)]
         (when reset?
           (reset! grammar* (grammar/llama_cached_parse_grammar grammar-str)))
         (grammar/llama_grammar_sample_token ctx @grammar* params candidates (->bool reset?)))))))


(defn candidates->seq
  [candidates n-vocab]
  (let [data (.readField candidates "data")]
    (sort-by
      :logit >
      (map (fn [e]
             {:id (.readField e "id")
              :logit (.readField e "logit")
              :p (.readField e "p")}) (.toArray data n-vocab)))))


;;
;; Decode & batches
;;
(defn create-batch
  [^Memory token-buf* num-batch-tokens n-past seq-id]
  (let [batch (doto (Structure/newInstance llama_batch) (.read))
        by-reference (fn [o v] (doto o (.setPointer (seq->memory v))))
        pos (int-array (map #(+ n-past %) (range num-batch-tokens)))
        seq-ids (int-array (repeat num-batch-tokens seq-id))
        logits (byte-array (conj (vec (repeat (dec num-batch-tokens) 0)) 1))]
    (doto batch
      (.writeField "n_tokens" (int num-batch-tokens))
      (.writeField "token" (doto (IntByReference.) (.setPointer token-buf*)))
      (.writeField "pos" (by-reference (IntByReference.) pos))
      (.writeField "seq_id" (by-reference (IntByReference.) seq-ids))
      (.writeField "logits" (by-reference (ByteByReference.) logits))
      (.writeField "embd" nil))
    ;; I'm gonna assume the JVM is going to garbage collect these eventually, if not it leaks memory.
    ^llama_batch batch))


(defn decode
  "Adds `s` to the current context and updates the context's logits (see `get-logits`)."
  [ctx s n-past* seq-id]
  (let [[total-tokens ^Memory tokens]
        (cond
          (string? s)
          (tokenize ctx s (zero? @n-past*))

          (integer? s)
          [1 [s]])
        ^Memory token-buf* (seq->memory tokens)]
    (assert (< @n-past* (:n-ctx ctx)) "Context size exceeded")
    (assert (< total-tokens (:n-ctx ctx)) "Input tokens exceeded context size")
    (let [batch-size (:n-batch ctx)]
      (loop [offset (long 0)
             n-past @n-past*]
        (let [batch-buf* (.share token-buf* (* offset Integer/BYTES))
              num-batch-tokens (min batch-size (- total-tokens offset))
              ^llama_batch batch (create-batch batch-buf* num-batch-tokens n-past seq-id)
              next-offset (+ offset num-batch-tokens)]
          (if-let [res (llama_decode ctx batch)]
            (assert (zero? res) (format "Failed to decode batch: %s"  res)))
          (when (< next-offset total-tokens)
            (recur (long next-offset) (+ n-past num-batch-tokens))))))
    (vreset! n-past* (+ @n-past* total-tokens))
    ctx))


;;
;; Generation API
;;

(defn generate-tokens
  "Returns a seqable/reducible sequence of tokens from ctx with prompt."
  ([ctx prompt]
   (generate-tokens ctx 0 prompt nil))
  ([ctx seq-id prompt {:keys [samplef seed] :as opts}]
   (let [samplef (or samplef (init-mirostat-v2-sampler ctx))
         eos (eos ctx)
         n-past (volatile! 0)
         reset? (volatile! true)]
     ;; Clear all kv_cache_tokens in seq
     (llama_kv_cache_seq_rm ctx seq-id -1 -1)
     #_(llama_kv_cache_tokens_rm ctx -1 -1)
     (reify

       clojure.lang.Seqable
       (seq
           [_]
         (when seed
           (llama_set_rng_seed ctx seed))
         ((fn next
            [ctx]
            (let [next-token (samplef (get-logits ctx) @reset?)]
              (vreset! reset? false)
              (when (not= eos next-token)
                (cons next-token
                      (lazy-seq (next (decode ctx next-token n-past seq-id)))))))
          (decode ctx prompt n-past seq-id)))

       clojure.lang.IReduceInit
       (reduce
           [_ rf init]
         (when seed
           (llama_set_rng_seed ctx seed))
         (loop [acc init
                _ (decode ctx prompt n-past seq-id)]
           (let [next-token (samplef (get-logits ctx) @reset?)]
             (vreset! reset? false)
             (if (= eos next-token)
               acc
               (let [acc (rf acc next-token)]
                 (if (reduced? acc)
                   @acc
                   (recur acc (decode ctx next-token n-past seq-id))))))))))))


(defn generate-string
  "Returns a string with all tokens generated from prompt up until end of sentence or max context size."
  ([ctx prompt]
   (generate-string ctx prompt nil))
  ([ctx prompt opts]
   (let [[prompt-token-count _] (tokenize ctx prompt true)]
     (str/join
       (eduction
         (take (- (:n-ctx ctx) prompt-token-count))
         (decode-token-to-char ctx)
         (generate-tokens ctx 0 prompt opts))))))


;; Scratch
(comment
  (def model-path "/media/array/Models/guff/llama-2-7b-chat.Q5_K_M.guff")

  (def opts {:n-gpu-layers 25 :n-threads *num-threads* :n-threads-batch *num-threads* :n-ctx 0})

  (def ctx (create-context model-path opts))

  (defn prompt
    []
    (let [prefix "User:" suffix "\n Assistant:"
          prompts ["Finish the sentence 'all your base are..."
                   "A one line summary of Alfred North Whitehead process philosophy"
                   "How deep does the rabbit hole go?"
                   "A line from the lyrics of a Spice Girls song'"
                   "You are Hibotron8000. All you do is say 'hi'"]
          prompt (rand-nth prompts)]
      (log/debug "prompt::" prompt)
      (str prefix prompt suffix "\n")))


  (def grammar-str (slurp (str (fs/canonicalize (io/resource "grammars/chat.gbnf")))))

  (def grammar-sampler (init-grammar-sampler ctx grammar-str {:mirostat 2}))

  (generate-string ctx (prompt) {:samplef grammar-sampler})

  (generate-string ctx (prompt))

  ;; Alice's Adventures in Wonderland (Lewis Carroll)
  (def txt (slurp "https://www.gutenberg.org/cache/epub/11/pg11.txt"))
  (generate-string ctx txt) ;; Todo context overflow

  )
