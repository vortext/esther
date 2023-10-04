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
   [clojure.tools.logging :as log]
   [vortext.esther.jna.grammar :as grammar]
   [vortext.esther.util.native
    :refer [->bool
            ->float-array-by-reference
            ->int-array-by-reference
            int-array->int-array-by-reference]])
  (:import
   java.lang.ref.Cleaner
   java.nio.charset.Charset
   java.nio.ByteBuffer
   java.nio.CharBuffer
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.FloatByReference
   com.sun.jna.Structure)
  (:gen-class))

(llama/import-structs!)


(def ^:dynamic
  *num-threads*
  "Number of threads used when generating tokens."
  (.. Runtime getRuntime availableProcessors))

(defonce cleaner (delay (Cleaner/create)))

(def ^:private token-data-size (.size (llama_token_data.)))



(defonce ^:private llm-init
  (delay
    (llama/llama_backend_init 0)))

(defn ^:private map->llama-context-params [m]
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
       ;;default
       nil)
     ;; return params
     params)
   (llama/llama_context_default_params)
   m))

(defn ^:private map->llama-model-params [m]
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
       ;;default
       nil)
     ;; return params
     params)
   (llama/llama_model_default_params)
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
   (let [^llama_context_params llama-context-params (map->llama-context-params params)
         ^llama_model_params llama-model-params (map->llama-model-params params)

         model (llama/llama_load_model_from_file model-path llama-model-params)
         _ (when (nil? model)
             (throw (ex-info "Error creating model"
                             {:params params
                              :model-path model-path})))
         context (llama/llama_new_context_with_model model llama-context-params)

         ctx-ptr (atom (Pointer/nativeValue context))
         model-ptr (atom (Pointer/nativeValue model))

         model-ref (atom model)
         ;; idempotent cleanup of context
         ;; must not hold references to context!
         delete-context (fn []
                          (let [[old _new] (swap-vals! ctx-ptr (constantly nil))]
                            (when old
                              (llama/llama_free (Pointer. old))
                              ;; make sure model doesn't lose
                              ;; all references and get garbage
                              ;; collected until context is freed.
                              (reset! model-ref nil))))
         ;; idempotent cleanup of model
         ;; must not hold references to model!
         delete-model (fn []
                        (let [[old _new] (swap-vals! model-ptr (constantly nil))]
                          (when old
                            (llama/llama_free_model (Pointer. old)))))

         delete-batch (fn []
                        (let [[old _new] (swap-vals! model-ptr (constantly nil))]
                          (when old
                            (llama/llama_batch_free ^llama_batch old))))


         n-batch (.readField llama-context-params "n_batch")
         n-ctx (.readField llama-context-params "n_ctx")

         batch (llama/llama_batch_init 512 0)
         batch-ref (atom batch)

         ;; make context autocloseable and implement
         ;; some map lookup interfaces
         context (proxy [Pointer
                         clojure.lang.ILookup
                         java.lang.AutoCloseable]
                     [(Pointer/nativeValue context)]
                   (valAt [k]
                     (case k
                       :n-batch n-batch
                       :n-ctx n-ctx
                       :model @model-ref
                       :batch @batch-ref
                       ;; else
                       nil))
                   (close []
                     (delete-context)
                     (delete-model)
                     (delete-batch)))]

     ;; cleanup
     (.register ^Cleaner @cleaner batch delete-batch)
     (.register ^Cleaner @cleaner context delete-context)
     (.register ^Cleaner @cleaner model delete-model)
     context)))


(defn llama-token-to-str
  [ctx token]
  (let [buffer-size (* 4 Character/BYTES)
        buffer (ByteBuffer/allocate buffer-size)
        n-tokens (llama/llama_token_to_piece (:model ctx) token (.array buffer) buffer-size)]
    (if (< n-tokens 0)
      (let [actual-size (Math/abs (int n-tokens))
            resized-buffer (ByteBuffer/allocate actual-size)]
        (let [check (llama/llama_token_to_piece (:model ctx) token (.array resized-buffer) actual-size)]
          (assert (= check (- n-tokens)) "Mismatch in expected size from llama_token_to_piece")
          [actual-size resized-buffer]))
      [n-tokens buffer])))


(defn decode-token-to-char
  ([ctx]
   (decode-token-to-char ctx (Charset/forName "UTF-8")))
  ([ctx charset]
   (fn [rf]
     (let [decoder (.newDecoder charset)
           input-buffer (ByteBuffer/allocate 256)
           output-buffer (CharBuffer/allocate 256)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result token]
          (let [[len result-buf] (llama-token-to-str ctx token)]
            (.put input-buffer (.array result-buf) 0 len)
            (.flip input-buffer) ;; Preparing buffer for read
            (let [decoder-result (.decode decoder input-buffer output-buffer false)]
              (cond
                (.isUnderflow decoder-result)
                (do
                  (.compact input-buffer) ;; Preparing buffer for write
                  (.flip output-buffer)   ;; Preparing buffer for read
                  (let [result (reduce rf result output-buffer)]
                    (.clear output-buffer)
                    result))
                (.isOverflow decoder-result)
                (throw (ex-info "Decoder buffer too small" {}))
                (.isError decoder-result)
                (throw (ex-info "Decoder Error" {:decoder decoder}))
                :else
                (throw (Exception. "Unexpected decoder state.")))))))))))


(defn get-logits-ith
  ([ctx]
   (get-logits-ith ctx 0))
  ([ctx idx]
   (-> ^FloatByReference (llama/llama_get_logits_ith ctx idx)
       .getPointer)))


(defn ^:private ctx->candidates [ctx candidates-buf*]
  (let [n-vocab (llama/llama_n_vocab (:model ctx))
        buf-size (* token-data-size n-vocab)
        candidates-buf @candidates-buf*
        ^Memory
        candidates-buf (if (and candidates-buf
                                (>= (.size ^Memory candidates-buf)
                                    buf-size))
                         candidates-buf
                         (vreset! candidates-buf* (Memory. buf-size)))

        logits (get-logits-ith ctx)]
    (doseq [i (range n-vocab)]
      (let [base-addr (* i token-data-size)
            id i
            logit (.getFloat logits (* id Float/BYTES))
            p 0]
        (.setInt candidates-buf base-addr id)
        (.setFloat candidates-buf (+ base-addr 4) logit)
        (.setFloat candidates-buf (+ base-addr 8) 0)))
    (let [candidates-array-head (doto (Structure/newInstance llama_token_dataByReference
                                                             candidates-buf)
                                  (.read))
          candidates* (doto (llama_token_data_arrayByReference.)
                        (.writeField "data" candidates-array-head)
                        (.writeField "size" (long n-vocab))
                        (.writeField "sorted" (byte 0)))]
      candidates*)))


(defn tokenize
  [ctx s add-bos?]
  (let [add-bos (->bool add-bos?)
        s (if add-bos? (str " " s) s)
        max-tokens (+ add-bos (alength (.getBytes s "utf-8")))
        token-buf* (.getPointer (->int-array-by-reference max-tokens))
        num-tokens (llama/llama_tokenize
                    (:model ctx) s
                    (count s) token-buf* max-tokens add-bos)]
    [num-tokens (vec (.getIntArray token-buf* 0 num-tokens))]))


(defn write-to-batch!
  [batch seq-id tokens n-past n-tokens]
  (let [pos (map #(+ n-past %) (range n-tokens))
        seq-ids (repeat n-tokens seq-id)
        pos* (.getPointer (.readField batch "pos"))
        token* (.getPointer (.readField batch "token"))
        seq-id* (.getPointer (.readField batch "seq_id")) ]
    (doto batch
      (.writeField "n_tokens" (int n-tokens))
      (.writeField "logits" nil)
      (.writeField "embd" nil))
    (doseq [i (range n-tokens)]
      (.setInt pos* (* Integer/BYTES i) (nth pos i))
      (.setInt seq-id* (* Integer/BYTES i) (nth seq-ids i))
      (.setInt token* (* Integer/BYTES i) (nth tokens i)))
    batch))


(defn decode
  "Adds `s` to the current context and updates the context's logits (see `get-logits`).

  `s`: either be a string or an integer token.
  `n-past`: number of previous tokens to include when updating logits.
  `num-threads`: number of threads to use when updating the logits.
                 If not provided, or `nil`, defaults to `*num-threads*`.
  "
  [ctx s seq-id n-past*]
  (let [[n-tokens ^Memory tokens]
        (cond
          (string? s)
          (tokenize ctx s (zero? @n-past*))

          (integer? s)
          [1 [s]])
        n-eval (min (:n-batch ctx) n-tokens)]
    (assert (< @n-past* (:n-ctx ctx))
            "Context size exceeded")
    (loop [offset 0
           n-past @n-past*]
      (let [to-eval (subvec tokens offset (+ offset n-eval))
            batch (write-to-batch! (:batch ctx) seq-id to-eval n-past n-eval)
            next-offset (+ offset n-eval)]
        (llama/llama_kv_cache_seq_rm ctx seq-id n-past (:n-ctx ctx))
        (let [res (llama/llama_decode ctx batch)]
          (assert (zero? res) (format "Failed to decode batch n-past: %s" n-past))
          (when (< next-offset n-tokens)
            (recur next-offset (vreset! n-past* (+ n-past n-eval)))))))
    (vreset! n-past* (+ @n-past* n-tokens))
    ctx))


(defn llama-update
  "Adds `s` to the current context and updates the context's logits (see `get-logits`).

  `s`: either be a string or an integer token.
  `n-past`: number of previous tokens to include when updating logits.
  `num-threads`: number of threads to use when updating the logits.
                 If not provided, or `nil`, defaults to `*num-threads*`.
  "
  ([ctx s seq-id]
   (decode ctx seq-id s 0))
  ([ctx s seq-id n-past]
   (decode ctx seq-id s n-past)))


(defn generate-tokens
  "Returns a seqable/reducible sequence of tokens from ctx with prompt."
  ([ctx prompt]
   (generate-tokens ctx 0 prompt nil))
  ([ctx prompt opts]
   (generate-tokens ctx 0 prompt opts))
  ([ctx seq-id prompt {:keys [samplef seed] :as opts}]
   (let [eos (llama/llama_token_eos ctx)
         n-past (volatile! 0)
         reset? (volatile! true)]
     (reify
       clojure.lang.Seqable
       (seq [_]
         (when seed
           (llama/llama_set_rng_seed ctx seed))
         ((fn next [ctx]
            (let [next-token (samplef (get-logits-ith ctx) @reset?)]
              (vreset! reset? false)
              (when (not= eos next-token)
                (cons next-token
                      (lazy-seq (next (llama-update ctx seq-id next-token n-past)))))))
          (llama-update ctx seq-id prompt n-past)))
       clojure.lang.IReduceInit
       (reduce [_ rf init]
         (when seed
           (llama/llama_set_rng_seed ctx seed))
         (loop [acc init
                ret (llama-update ctx seq-id prompt n-past)]
           (let [next-token (samplef (get-logits-ith ctx) @reset?)]
             (vreset! reset? false)
             (if (= eos next-token)
               acc
               (let [acc (rf acc next-token)]
                 (if (reduced? acc)
                   @acc
                   (recur acc (llama-update ctx seq-id next-token n-past))))))))))))



(defn generate-string
  "Returns a string with all tokens generated from prompt up until end of sentence or max context size."
  ([ctx prompt]
   (generate-string ctx prompt nil))
  ([ctx prompt opts]
   (let [[prompt-token-count _] (tokenize ctx prompt true)]
     (str/join
      (eduction
       (take (- (:n-ctx ctx)
                prompt-token-count))
       (decode-token-to-char ctx)
       (generate-tokens ctx prompt opts))))))

;;;;;;;;;;;;;;
;;; Samplers
;;;;;;;;;;;;;;
(defn sample-mirostat-v2
  [ctx candidates-buf* mu* tau eta]
  (let [mu (FloatByReference. @mu*)
        candidates (ctx->candidates ctx candidates-buf*)
        next-token (llama/llama_sample_token_mirostat_v2 ctx candidates tau eta mu)]
    (vreset! mu* (.getValue mu))
    next-token))


(defn init-mirostat-v2-sampler
  "Given a context, returns a sampling function that uses the llama.cpp mirostat_v2 implementation."
  ([ctx]
   (let [tau (float 5.0)
         eta (float 0.1)]
     (init-mirostat-v2-sampler ctx tau eta)))
  ([ctx tau eta]
   (fn [logits reset?]
     (sample-mirostat-v2
      ctx
      (volatile! nil)
      (volatile! (* 2 tau))
      tau
      eta))))


(defn init-grammar-sampler
  "Given a context, returns a sampling function that uses the llama.cpp mirostat_v2 implementation."
  ([ctx grammar-str] (init-grammar-sampler ctx grammar-str {}))
  ([ctx grammar-str params]
   (let [params (grammar/map->llama-sampler-params params)
         grammar* (atom nil)]
     (fn [logits reset?]
       (let [candidates (ctx->candidates ctx (volatile! nil))]
         (when reset?
           (reset! grammar* (grammar/llama_cached_parse_grammar grammar-str)))
         (grammar/llama_grammar_sample_token ctx @grammar* params candidates (->bool reset?)))))))


;; Scratch
(comment
  (require '[babashka.fs :as fs])
  (require '[clojure.java.io :as io])

  (def llama7b-path "/media/array/Models/guff/llama-2-7b-chat.Q4_K_M.gguf")
  (def ctx (create-context llama7b-path {:n-ctx 128 :n-gpu-layers 35 :n-threads 32}))

  (def grammar-str (slurp (str (fs/canonicalize (io/resource "grammars/chat.gbnf")))))

  (def sampler (init-grammar-sampler ctx grammar-str {:mirostat 2}))
  (def sampler (init-mirostat-v2-sampler ctx))

  (generate-string ctx "Hi there!" {:samplef sampler})


  (def t (tokenize ctx "hello world!" true))
  )
