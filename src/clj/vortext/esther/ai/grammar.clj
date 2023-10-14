(ns vortext.esther.ai.grammar
  (:refer-clojure :exclude [remove printf])
  (:gen-class)
  ; [WARNING]
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [com.phronemophobic.clong.gen.jna :as gen]))


(def library-options
  {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"})


(def shared-lib
  (str (fs/canonicalize "native/llama.cpp/build/examples/grammar/libgrammar.so")))


(def ^:no-doc library
  (com.sun.jna.NativeLibrary/getInstance
    shared-lib library-options))


(def api
  (with-open [rdr (io/reader (io/resource "api/grammar.edn"))
              rdr (java.io.PushbackReader. rdr)]
    (edn/read rdr)))


(gen/def-api library api)


(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs!
    []
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
