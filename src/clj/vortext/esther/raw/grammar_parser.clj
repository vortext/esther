(ns vortext.esther.raw.grammar-parser
  (:require
   [babashka.fs :as fs])
  (:import
   com.sun.jna.Pointer))

(def library-options
  {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"})

(def shared-lib
  (str (fs/canonicalize "native/llama.cpp/build/examples/grammar/libgrammar.so")))

(def ^:no-doc library
  (com.sun.jna.NativeLibrary/getInstance
   shared-lib library-options))


(def ^:private llama-cached-parse-grammar
  (.getFunction ^com.sun.jna.NativeLibrary library
                "llama_cached_parse_grammar"))

(defn ^Pointer init-grammar
  [grammar-str]
  (.invoke
   ^com.sun.jna.Function llama-cached-parse-grammar
   Pointer (to-array [grammar-str])))
