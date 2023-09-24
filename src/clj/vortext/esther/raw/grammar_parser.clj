(ns vortext.esther.raw.grammar-parser
  (:require [com.phronemophobic.clong.gen.jna :as gen]
            [clojure.edn :as edn]
            [babashka.fs :as fs]
            [clojure.java.io :as io]))

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
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))
