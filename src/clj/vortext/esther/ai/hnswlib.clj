(ns vortext.esther.ai.hnswlib
  (:require
   [babashka.fs :as fs]
   [com.phronemophobic.clong.clang :as clong]
   [com.phronemophobic.clong.gen.jna :as gen]
   [clojure.tools.logging :as log])
  )

(comment
  (def header-path
    (str (fs/canonicalize (fs/path "native/hnswlib/bindings.h"))))

  (def library-path
    (str (fs/canonicalize (fs/path "native/hnswlib/lib/libhnsw.so"))))

  (def hnswlib
    (com.sun.jna.NativeLibrary/getInstance library-path))

  (def api (clong/easy-api header-path))

  (gen/def-api hnswlib api)
  )
