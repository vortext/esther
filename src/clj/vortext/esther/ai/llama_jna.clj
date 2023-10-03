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
   [vortext.esther.util.native
    :refer [->bool
            ->float-array-by-reference
            ->int-array-by-reference
            int-array->int-array-by-reference
            boolean-array->byte-array-by-reference
            ptr->int-array]])
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
   com.sun.jna.Structure)
  (:gen-class))

(llama/import-structs!)

(def ^:dynamic
  *num-threads*
  "Number of threads used when generating tokens."
  (.. Runtime getRuntime availableProcessors))

(defonce cleaner (delay (Cleaner/create)))

(def ^:private token-data-size (.size (llama_token_data.)))

(def ->model (fn [ctx] (llama/llama_get_model ctx)))

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
   (let [threads {:n-threads-batch *num-threads*
                  :n-threads *num-threads*}
         ^llama_context_params llama-context-params (map->llama-context-params (merge threads params))
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

         n-batch (.readField llama-context-params "n_batch")
         n-ctx (.readField llama-context-params "n_ctx")

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
                       ;; else
                       nil))
                   (close []
                     (delete-context)
                     (delete-model)))]

     ;; cleanup
     (.register ^Cleaner @cleaner context delete-context)
     (.register ^Cleaner @cleaner model delete-model)
     context)))


(defn llama-token-to-str
  [ctx token]
  (let [buffer-size (* 4 Character/BYTES)
        buffer (ByteBuffer/allocate buffer-size)
        n-tokens (llama/llama_token_to_piece (->model ctx) token (.array buffer) buffer-size)]
    (if (< n-tokens 0)
      (let [actual-size (Math/abs (int n-tokens))
            resized-buffer (ByteBuffer/allocate actual-size)]
        (let [check (llama/llama_token_to_piece (->model ctx) token (.array resized-buffer) actual-size)]
          (assert (= check (- n-tokens)) "Mismatch in expected size from llama_token_to_piece")
          [actual-size resized-buffer]))
      [n-tokens buffer])))


(defn decode-token-to-char
  [ctx]
  (fn [rf]
    (let [charset (Charset/forName "UTF-8")
          decoder (.newDecoder charset)
          input-buffer (ByteBuffer/allocate 256)
          output-buffer (CharBuffer/allocate 256)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result token]
         (let [[len result-buf] (llama-token-to-str ctx token)]
           (.put input-buffer (.array result-buf) 0 len) ;; Pay attention to how the input is fed to the buffer
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
               (throw (Exception. "Unexpected decoder state."))))))))))



(defn get-logits
  "Returns a copy of the current context's logits as a float array."
  [ctx seq-id]
  (-> ^FloatByReference (llama/llama_get_logits_ith ctx seq-id)
      .getPointer))


(defn ctx->candidates
  [ctx seq-id]
  (let [n-vocab (llama/llama_n_vocab (->model ctx))
        buf-size (* token-data-size n-vocab)
        ^Memory candidates-buf (doto (Memory. buf-size) (.clear))
        logits (get-logits ctx seq-id)]
    (doseq [id (range n-vocab)]
      (let [base-addr (* id token-data-size)
            logit (.getFloat logits (* id Float/BYTES))]
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
                    (->model ctx) s
                    (count s) token-buf* max-tokens add-bos)]
    (log/debug "tokenize::tokenized" num-tokens)
    (into [] (take-while pos? (ptr->int-array token-buf*)))))


(defn create-batch
  [seq-id tokens n-past]
  (let [n-tokens (count tokens)
        logits (-> (conj (into [] (repeat (dec n-tokens) (->bool false))) (->bool true))
                   byte-array)
        token (-> tokens int-array)
        seq-id (-> (repeat n-tokens seq-id) int-array)
        pos (-> (map #(+ n-past %) (range n-tokens)) int-array)]
    (doto (llama_batch.)
      (.writeField "n_tokens" n-tokens)
      (.writeField "token" (int-array->int-array-by-reference token))
      (.writeField "embd" nil)
      (.writeField "logits" (boolean-array->byte-array-by-reference logits))
      (.writeField "seq_id" (int-array->int-array-by-reference seq-id))
      (.writeField "pos" (int-array->int-array-by-reference pos)))))



(defn batched-decode
  [ctx seq-id tokens n-past]
  (assert (< @n-past (:n-ctx ctx))
          "Context size exceeded")
  (loop [offset 0]
    (let [n-tokens (count tokens)
          n-eval (min (:n-batch ctx) n-tokens)
          eval-tokens (subvec tokens offset (+ offset n-eval))
          batch (create-batch seq-id eval-tokens @n-past)]
      (llama/llama_kv_cache_tokens_rm ctx @n-past -1)
      (if-let [_ (neg? (llama/llama_decode ctx batch))]
        (throw (Exception. (str "failed to decode n-tokens" n-tokens " n-past " @n-past)))
        (let [next-offset (+ offset n-eval)]
          (vreset! n-past (+ @n-past n-eval))
          (when (< next-offset n-tokens) (recur next-offset))))))
  ctx)

(def ^:dynamic
  *num-threads*
  "Number of threads used when generating tokens."
  (.. Runtime getRuntime availableProcessors))

(defn generate-tokens
  [ctx seq-id {:keys [samplef seed]} n-past]
  (let [eos (llama/llama_token_eos ctx)
        reset? (volatile! true)]
    (reify
      clojure.lang.IReduceInit
      (reduce [_ rf init]
        (when seed
          (llama/llama_set_rng_seed ctx seed))
        (loop [acc init
               ret nil]
          (let [next-token (samplef (ctx->candidates ctx seq-id) @reset?)]
            (if (= eos next-token)
              acc
              (let [acc (rf acc next-token)]
                (if (reduced? acc)
                  @acc
                  (recur acc (batched-decode ctx seq-id [next-token] n-past))))))))

      clojure.lang.Seqable
      (seq [_]
        (when seed
          (llama/llama_set_rng_seed ctx seed))
        ((fn next [ctx]
           (let [next-token (samplef (ctx->candidates ctx seq-id) @reset?)]
             (when (not= eos next-token)
               (vreset! reset? false)
               (cons next-token
                     (lazy-seq (next (batched-decode ctx seq-id [next-token] n-past)))))))
         ctx)))))



(defn generate-string
  "Returns a string with all tokens generated from prompt up until end of sentence or max context size."
  ([ctx prompt opts]
   (generate-string ctx prompt opts 0))
  ([ctx prompt opts seq-id]
   (let [n-ctx (:n-ctx ctx)
         _ (llama/llama_kv_cache_seq_rm ctx seq-id 0 (:n-ctx ctx))
         prompt-tokens (tokenize ctx prompt true)
         n-past (volatile! 0)
         _ (batched-decode ctx seq-id prompt-tokens n-past)]
     (str/join
      (eduction
       (take (- n-ctx (count prompt-tokens)))
       (decode-token-to-char ctx)
       (generate-tokens ctx seq-id opts n-past))))))


;; Scratch
(comment
  (require '[babashka.fs :as fs])
  (require '[clojure.java.io :as io])

  (def llama7b-path "/media/array/Models/guff/llama-2-7b-chat.Q4_K_M.gguf")
  (def ctx (create-context llama7b-path {:n-ctx 4096 :n-gpu-layers 35}))

  (def grammar-str (slurp (str (fs/canonicalize (io/resource "grammars/chat.gbnf")))))

  (def sampler (grammar/init-llama-sampler ctx grammar-str {:mirostat 2}))

  (generate-string ctx "Hi there!" {:samplef sampler})

  )
