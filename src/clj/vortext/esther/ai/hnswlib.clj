(ns vortext.esther.ai.hnswlib
  (:require
   [babashka.fs :as fs]
   [com.phronemophobic.clong.clang :as clong]
   [com.phronemophobic.clong.gen.jna :as gen]
   [clojure.tools.logging :as log])
  (:import
   java.io.PushbackReader
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.PointerByReference
   com.sun.jna.Library
   com.sun.jna.ptr.LongByReference
   com.sun.jna.Structure)
  )

(def header-path
  (str (fs/canonicalize (fs/path "native/hnswlib/bindings.h"))))

(def library-path
  (str (fs/canonicalize (fs/path "native/hnswlib/lib/libhnsw.so"))))

(def hnswlib
  (com.sun.jna.NativeLibrary/getInstance library-path))

(def api (clong/easy-api header-path))

(gen/def-api hnswlib api)

(def dim 128) ; Example dimension
(def max-elements 10000) ; Example max elements
(def M 16) ; Example M value
(def ef-construction 200) ; Example ef_construction value

