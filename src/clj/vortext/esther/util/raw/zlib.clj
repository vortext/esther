(ns vortext.esther.util.raw.zlib
  (:refer-clojure :exclude [read sync]) ;; !!!
  (:require
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [vortext.esther.util.json :refer [read-transit-from-file write-transit-to-file]]
   [com.phronemophobic.clong.clang :as clong]
   [com.phronemophobic.clong.gen.jna :as gen])
  (:import
   java.io.PushbackReader
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.ptr.LongByReference
   com.sun.jna.Structure)
  (:gen-class))

(def h "zlib.h")
(def api-file (str (fs/canonicalize (fs/path (io/resource "api") "zlib.json"))))
(def api-def (read-transit-from-file api-file))

(def zlib (com.sun.jna.NativeLibrary/getInstance "z"))

(gen/def-api zlib api-def)


;;; Scratch
(comment
  (defn write-api ;; I used this to write the API
    [h out]
    (let [h-file (str (fs/canonicalize (io/resource (str "h/" h))))
          api (clong/easy-api h-file)]
      (write-transit-to-file api (str out)))))
