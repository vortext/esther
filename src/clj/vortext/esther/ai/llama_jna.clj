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

(ns vortext.esther.ai.llama-jna
  (:require
   [clojure.string :as str]
   [vortext.esther.jna.llama :as llama]
   [vortext.esther.jna.grammar :as grammar]
   [clojure.tools.logging :as log]
   [clojure.core.cache.wrapped
    :refer [soft-cache-factory]]
   [vortext.esther.util.native
    :refer [->bool ->float-array-by-reference]])
  (:import
   java.lang.ref.Cleaner
   java.nio.charset.CodingErrorAction
   java.nio.charset.CharsetDecoder
   java.nio.charset.Charset
   java.nio.ByteBuffer
   java.nio.CharBuffer
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.FloatByReference
   com.sun.jna.Structure))

(llama/import-structs!)


(defonce cleaner (delay (Cleaner/create)))

(def ^:dynamic
  *num-threads*
  "Number of threads used when generating tokens."
  (.. Runtime getRuntime availableProcessors))

(def ^:private token-data-size (.size (llama_token_data.)))

(defonce ^:private llm-init
  (delay
    (llama/llama_backend_init 0)))

(defn ^:private map->llama-params [m]
  (reduce-kv
   (fn [^llama_context_params params k v]
     (case k
       :seed (.writeField params "seed" (int v))
       :n-ctx (.writeField params "n_ctx" (int v))
       :n-batch (.writeField params "n_batch" (int v))
       :n-gpu-layers (.writeField params "n_gpu_layers" (int v))
       :main-gpu (.writeField params "main_gpu" (int v))
       :tensor-split (.writeField params "tensor_split" (->float-array-by-reference  v))
       :rope-freq-base (.writeField params "rope_freq_base" (float v))
       :rope-freq-scale (.writeField params "rope_freq_scale" (float v))
       ;; :progress-callback (.writeField params "progress_callback" v)
       ;; :progress-callback-user-data (.writeField params "progress_callback_user_data" v)
       :low-vram (.writeField params "low_vram" (->bool v))
       :mul_mat_q (.writeField params "mul_mat_q" (->bool v))
       :f16-kv (.writeField params "f16_kv" (->bool v))
       :logits-all (.writeField params "logits_all" (->bool v))
       :vocab-only (.writeField params "vocab_only" (->bool v))
       :use-mmap (.writeField params "use_mmap" (->bool v))
       :use-mlock (.writeField params "use_mlock" (->bool v))
       :embedding (.writeField params "embedding" (->bool v))
       ;;default
       nil)
     ;; return params
     params)
   (llama/llama_context_default_params)
   m))

(defn create-context
  "Create and return an opaque llama context.

  `model-path` should be an absolute or relative path to a F16, Q4_0, Q4_1, Q5_0, Q5_1, or Q8_0 ggml model.

  An optional map of parameters may be passed for parameterizing the model.
  The following keys map to their corresponding llama.cpp equivalents:
  - `:seed`: RNG seed, -1 for random
  - `:n-ctx`: text context
  - `:n-batch`: prompt processing batch size
  - `:n-gpu-layers`: number of layers to store in VRAM
  - `:main-gpu`: the GPU that is used for scratch and small tensors
  - `:tensor-split`: how to split layers across multiple GPUs
  - `:rope-freq-base`: RoPE base frequency
  - `:rope-freq-scale`: RoPE frequency scaling factor
  - `:low-vram`: if true, reduce VRAM usage at the cost of performance
  - `:mul_mat_q`: if true, use experimental mul_mat_q kernels
  - `:f16-kv`: use fp16 for KV cache
  - `:logits-all`: the llama_eval() call computes all logits, not just the last one
  - `:vocab-only`: only load the vocabulary, no weights
  - `:use-mmap`: use mmap if possible
  - `:use-mlock`: force system to keep model in RAM
  - `:embedding`: embedding mode only

  Resources can be freed by calling .close on the returned context.
  Using a closed context is undefined and will probably crash the JVM.

  Contexts are not thread-safe. Using the same context on multiple threads
  is undefined and will probably crash the JVM.
  "
  ([model-path]
   (create-context model-path nil))
  ([model-path params]
   @llm-init
   (let [^llama_context_params llama-params (map->llama-params params)
         model (llama/llama_load_model_from_file model-path llama-params)
         _ (when (nil? model)
             (throw (ex-info "Error creating model"
                             {:params params
                              :model-path model-path})))
         context (llama/llama_new_context_with_model model llama-params)

         ctx-ptr (atom (Pointer/nativeValue context))
         model-ptr (atom (Pointer/nativeValue model))

         model-ref (atom model)
         ;; idempotent cleanup of context
         ;; must not hold references to context!
         delete-context (fn []
                          (let [[old new] (swap-vals! ctx-ptr (constantly nil))]
                            (when old
                              (llama/llama_free (Pointer. old))
                              ;; make sure model doesn't lose
                              ;; all references and get garbage
                              ;; collected until context is freed.
                              (reset! model-ref nil))))
         ;; idempotent cleanup of model
         ;; must not hold references to model!
         delete-model (fn []
                        (let [[old new] (swap-vals! model-ptr (constantly nil))]
                          (when old
                            (llama/llama_free_model (Pointer. old)))))

         n-batch (.readField llama-params "n_batch")
         ;; make context autocloseable and implement
         ;; some map lookup interfaces
         context (proxy [Pointer
                         clojure.lang.ILookup
                         java.lang.AutoCloseable]
                     [(Pointer/nativeValue context)]
                   (valAt [k]
                     (case k
                       :n-batch n-batch
                       :params params
                       :model @model-ref
                       ;; else
                       nil))
                   (close []
                     (delete-context)
                     (delete-model)))]

     ;; cleanup
     (.register ^Cleaner @cleaner context delete-context)
     (.register ^Cleaner @cleaner model delete-model)

     context)))

(defonce ^:private
  token-bufs
  (soft-cache-factory {}))

(defn ^:private get-token-buf [ctx n]
  (get
   (swap! token-bufs
          (fn [m]
            (let [buf (get m ctx)]
              (if (and buf
                       (>= (.size ^Memory buf)
                           (* 4 n)))
                m
                (assoc m ctx (Memory. (* 4 n)))))))
   ctx))

(defn ^:private tokenize [ctx s add-bos?]
  (let [add-bos (if add-bos? 1 0)
        s (if add-bos? (str " " s) s)
        max-tokens (+ add-bos (alength (.getBytes ^String s "utf-8")))
        token-buf (get-token-buf ctx max-tokens)
        num-tokens (llama/llama_tokenize ctx s (count s) token-buf max-tokens add-bos)]
    [num-tokens token-buf]))

(defn llama-update
  "Adds `s` to the current context and updates the context's logits (see `get-logits`).

  `s`: either be a string or an integer token.
  `n-past`: number of previous tokens to include when updating logits.
  `num-threads`: number of threads to use when updating the logits.
                 If not provided, or `nil`, defaults to `*num-threads*`.
  "
  ([ctx s]
   (llama-update ctx s (llama/llama_get_kv_cache_token_count ctx) *num-threads*))
  ([ctx s n-past]
   (llama-update ctx s n-past *num-threads*))
  ([ctx s n-past num-threads]
   (let [num-threads (or num-threads *num-threads*)
         [total-tokens ^Memory token-buf]
         (cond
           (string? s)
           (tokenize ctx s (zero? n-past))

           (integer? s)
           (let [^Memory buf (get-token-buf ctx 1)]
             [1 (doto buf
                  (.setInt 0 s))]))]
     (assert (< n-past (llama/llama_n_ctx ctx))
             "Context size exceeded")

     (let [batch-size (:n-batch ctx)]
       (loop [offset (int 0)
              n-past (int n-past)]
         (let [batch-buf (.share token-buf (* offset 4))
               num-batch-tokens (min batch-size (- total-tokens offset))]
           (llama/llama_eval ctx batch-buf num-batch-tokens n-past num-threads)
           (let [next-offset (+ offset num-batch-tokens)]
             (when (< next-offset total-tokens)
               (recur (int next-offset)
                      (int (+ n-past num-batch-tokens))))))))
     ctx)))


(defn ctx->candidates
  [ctx candidates-buf*]
  (let [n-vocab (llama/llama_n_vocab ctx)
        buf-size (* token-data-size n-vocab)
        candidates-buf @candidates-buf*
        ^Memory
        candidates-buf (if (and candidates-buf
                                (>= (.size ^Memory candidates-buf)
                                    buf-size))
                         candidates-buf
                         (vreset! candidates-buf* (Memory. buf-size)))

        logits (-> ^FloatByReference (llama/llama_get_logits ctx)
                   .getPointer
                   (.getFloatArray 0 n-vocab))]
    (doseq [i (range n-vocab)]
      (let [base-addr (* i token-data-size)
            id i
            logit (aget logits id)
            p 0]
        (.setInt candidates-buf base-addr id)
        (.setFloat candidates-buf (+ base-addr 4) logit)
        (.setFloat candidates-buf (+ base-addr 8) 0)))
    (let [candidates-array-head (doto (Structure/newInstance
                                       llama_token_dataByReference
                                       candidates-buf)
                                  (.read))
          candidates* (doto (llama_token_data_arrayByReference.)
                        (.writeField "data" candidates-array-head)
                        (.writeField "size" (long n-vocab))
                        (.writeField "sorted" (byte 0)))]
      candidates*)))


(defn get-logits
  "Returns a copy of the current context's logits as a float array."
  [ctx]
  (let [n-vocab (llama/llama_n_vocab ctx)]
    (-> ^FloatByReference (llama/llama_get_logits ctx)
        .getPointer
        (.getFloatArray 0 n-vocab))))


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

(defn ^:private preserving-reduced
  [rf]
  #(let [ret (rf %1 %2)]
     (if (reduced? ret)
       (reduced ret)
       ret)))


(defn llama-token-to-str
  [ctx token]
  (let [initial-size 8
        result (ByteBuffer/allocate initial-size)
        n-tokens (llama/llama_token_to_piece ctx token (.array result) initial-size)]
    (if (< n-tokens 0)
      (let [actual-size (Math/abs (int n-tokens))
            resized-result (ByteBuffer/allocate actual-size)
            check (llama/llama_token_to_piece ctx token (.array resized-result) actual-size)]
        (assert (= check (- n-tokens)) "Mismatch in expected size from llama_token_to_piece")
        [actual-size resized-result])
      [n-tokens result])))


(defn decode-token-to-char
  "Returns a transducer that expects a stream of llama tokens
  and outputs a stream of decoded chars.

  The transducer will buffer intermediate results until enough
  bytes to decode a character are available."
  ([ctx]
   (decode-token-to-char ctx nil))
  ([ctx opts]
   (let [^Charset charset
         (cond
           (nil? opts) (Charset/forName "UTF-8")
           (map? opts) (or (:charset opts)
                           (Charset/forName "UTF-8"))
           ;; for backwards compatibility
           :else opts)
         flush? (:flush? opts)]
     (fn [rf]
       (let [decoder (doto (.newDecoder charset)
                       (.onMalformedInput CodingErrorAction/REPLACE)
                       (.onUnmappableCharacter CodingErrorAction/REPLACE))

             input-buffer (ByteBuffer/allocate 256)
             output-buffer (CharBuffer/allocate 256)

             rrf (preserving-reduced rf)]
         (fn
           ([] (rf))
           ([result]
            (if flush?
              (do
                (.flip input-buffer)
                (let [result
                      (let [ ;; Invoke the decode method one final time, passing true for the endOfInput argument; and then
                            decoder-result1 (.decode decoder input-buffer output-buffer true)
                            ;; Invoke the flush method so that the decoder can flush any internal state to the output buffer.
                            decoder-result2 (.flush decoder output-buffer)]
                        (if (and (.isUnderflow decoder-result1)
                                 (.isUnderflow decoder-result2))
                          (do
                            (.flip output-buffer)
                            (let [result (reduce rrf result output-buffer)]
                              (.clear output-buffer)
                              result))
                          ;; else
                          (throw (Exception. "Unexpected decoder state."))))]
                  (rf result)))
              ;; else no flush
              (rf result)))
           ([result token]
            (let [[len result-buf] (llama-token-to-str ctx token)]
              (.put input-buffer (.slice ^ByteBuffer result-buf (int 0) (int len)))
              (.flip input-buffer)

              ;; Invoke the decode method zero or more times, as long as
              ;; additional input may be available, passing false for the
              ;; endOfInput argument and filling the input buffer and flushing
              ;; the output buffer between invocations;
              (let [decoder-result (.decode decoder input-buffer output-buffer false)]
                (cond
                  (.isUnderflow decoder-result)
                  (do
                    (.compact input-buffer)
                    (.flip output-buffer)
                    (let [result (reduce rrf result output-buffer)]
                      (.clear output-buffer)
                      result))

                  (.isOverflow decoder-result)
                  (throw (ex-info "Decoder buffer too small" {}))

                  (.isError decoder-result)
                  (throw (ex-info "Decoder Error" {:decoder decoder}))

                  :else
                  (throw (Exception. "Unexpected decoder state."))))))))))))


(defn decode-token
  "Returns a transducer that expects a stream of llama tokens
  and outputs a stream of strings.

  The transducer will buffer intermediate results until enough
  bytes to decode a character are available. Also combines
  surrogate pairs of characters."
  ([ctx]
   (decode-token ctx (Charset/forName "UTF-8")))
  ([ctx ^Charset charset]
   (comp
    (decode-token-to-char ctx charset)
    (char->str))))


(defn generate-tokens
  "Returns a seqable/reducible sequence of tokens from ctx with prompt."
  ([ctx prompt]
   (generate-tokens ctx prompt nil))
  ([ctx prompt {:keys [samplef
                       num-threads
                       seed]
                :as opts}]
   (let [eos (llama/llama_token_eos ctx)
         kv-cache-token-count #(llama/llama_get_kv_cache_token_count ctx)
         reset? (volatile! true)]
     (reify
       clojure.lang.Seqable
       (seq [_]
         (when seed
           (llama/llama_set_rng_seed ctx seed))
         ((fn next [ctx]
            (let [next-token (samplef (get-logits ctx) @reset?)]
              (when (not= eos next-token)
                (vreset! reset? false)
                (cons next-token
                      (lazy-seq (next (llama-update ctx next-token (kv-cache-token-count) num-threads)))))))
          (llama-update ctx prompt 0 num-threads)))
       clojure.lang.IReduceInit
       (reduce [_ rf init]
         (when seed
           (llama/llama_set_rng_seed ctx seed))
         (loop [acc init
                ret (llama-update ctx prompt 0 num-threads)]
           (let [next-token (samplef (get-logits ctx) @reset?)]
             (when (not= eos next-token)
               (vreset! reset? false)
               (let [acc (rf acc next-token)]
                 (if (reduced? acc)
                   @acc
                   (recur acc (llama-update ctx next-token (kv-cache-token-count) num-threads))))))))))))

(defn generate
  "Returns a seqable/reducible sequence of strings generated from ctx with prompt."
  ([ctx prompt]
   (generate ctx prompt nil))
  ([ctx prompt opts]
   (eduction
    (decode-token ctx)
    (generate-tokens ctx prompt opts))))


(defn generate-string
  "Returns a string with all tokens generated from prompt up until end of sentence or max context size."
  ([ctx prompt]
   (generate-string ctx prompt nil))
  ([ctx prompt opts]
   (let [[prompt-token-count _] (tokenize ctx prompt true)]
     (log/debug "prompt-token-count" prompt-token-count)
     (str/join
      (eduction
       (take (- (llama/llama_n_ctx ctx)
                prompt-token-count))
       (decode-token-to-char ctx)
       (generate-tokens ctx prompt opts))))))


;; Scratch
(comment
  (require '[babashka.fs :as fs])
  (require '[clojure.java.io :as io])

  (def llama7b-path "/media/array/Models/guff/llama-2-7b-chat.Q4_K_M.gguf")
  (def ctx (create-context llama7b-path {:n-ctx 512 :n-gpu-layers 35}))


  (def grammar-str (slurp (str (fs/canonicalize (io/resource "grammars/chat.gbnf")))))

  (def sampler (grammar/init-llama-sampler ctx ctx->candidates grammar-str {}))

  (generate-string ctx "Hi there!" {:samplef sampler})


  )
