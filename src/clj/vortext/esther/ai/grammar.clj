(ns vortext.esther.ai.grammar
  (:refer-clojure :exclude [remove printf]) ;; [WARNING]
  (:require
   [babashka.fs :as fs]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [com.phronemophobic.clong.gen.jna :as gen]))


(defn load-library [lib-name]
  (try
    (clojure.lang.RT/loadLibrary lib-name)
    (log/info (str "Successfully loaded library: " lib-name))
    (catch UnsatisfiedLinkError e
      (log/error (str "Failed to load library: " lib-name " - " (.getMessage e)))
      (throw e))))

;; Load libllama.so and libgrammar.so
(load-library "llama")
(load-library "grammar")

(def ^:no-doc library
  (com.sun.jna.NativeLibrary/getInstance
   "grammar"
   {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"}))


(def api (with-open [rdr (io/reader (io/resource "api/grammar.edn"))
                     rdr (java.io.PushbackReader. rdr)]
           (edn/read rdr)))

(gen/def-api library api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))

(import-structs!)

(defn map->llama-sampler-params
  [m]
  (reduce-kv
   (fn [^llama_sampler_params params k v]
     (case k
       :temp (.writeField params "temp" (float v))
       :repeat-penalty (.writeField params "repeat_penalty" (float v))
       :repeat-last-n (.writeField params "repeat_last_n" (int v))
       :frequency-penalty (.writeField params "frequency_penalty" (float v))
       :presence-penalty (.writeField params "presence_penalty" (float v))

       :mirostat (.writeField params "mirostat" (int v))
       :mirostat-tau (.writeField params "mirostat_tau" (float v))
       :mirostat-eta (.writeField params "mirostat_eta" (float v))

       ;; default
       nil)
     ;; return params
     params)
   (llama_sampler_default_params)
   m))
