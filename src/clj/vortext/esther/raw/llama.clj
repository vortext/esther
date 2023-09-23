(ns vortext.esther.raw.llama
  (:refer-clojure :exclude [remove printf]) ;; [WARNING]
  (:require [com.phronemophobic.clong.gen.jna :as gen]
            [clojure.edn :as edn]
            [babashka.fs :as fs]
            [clojure.java.io :as io]))

(def libllama-options
  {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"})

(def llama-so
  (str (fs/canonicalize "native/llama.cpp/build/libllama.so")))

(def ^:no-doc libllama
  (com.sun.jna.NativeLibrary/getInstance
   llama-so libllama-options))

(def api
  (with-open [rdr (io/reader (io/resource "api/llama.edn"))
              rdr (java.io.PushbackReader. rdr)]
    (edn/read rdr)))

(gen/def-api libllama api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))
