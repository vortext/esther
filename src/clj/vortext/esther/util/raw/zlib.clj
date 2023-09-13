(ns vortext.esther.util.raw.zlib
  (:refer-clojure :exclude [read sync])
  (:gen-class)
  ; !!!
  (:require
    [babashka.fs :as fs]
    [clojure.java.io :as io]
    [com.phronemophobic.clong.clang :as clong]
    [com.phronemophobic.clong.gen.jna :as gen]
    [vortext.esther.util.json :refer [read-transit-from-file write-transit-to-file]])
  (:import
    (com.sun.jna
      Memory
      Pointer
      Structure)
    (com.sun.jna.ptr
      LongByReference
      PointerByReference)
    java.io.PushbackReader))


(def h "zlib.h")
(def api-file (str (fs/canonicalize (fs/path (io/resource "api") "zlib.json"))))
(def api-def (read-transit-from-file api-file))

(def zlib (com.sun.jna.NativeLibrary/getInstance "z"))

(gen/def-api zlib api-def)


;; Scratch
(comment
  (defn write-api ;; I used this to write the API
    [h out]
    (let [h-file (str (fs/canonicalize (io/resource (str "h/" h))))
          api (clong/easy-api h-file)]
      (write-transit-to-file api (str out)))))
