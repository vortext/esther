(ns vortext.esther.raw.llama
  (:refer-clojure :exclude [remove printf]) ;; [WARNING]
  (:require [com.phronemophobic.clong.gen.jna :as gen]
            [clojure.edn :as edn]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [com.rpl.specter :as specter]))

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

(defn ^:private write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))


;; can find by calling clang -### empty-file.h
(def clang-args
  ["-resource-dir"
   "/usr/lib/llvm-14/lib/clang/14.0.0"

   "-internal-isystem"
   "/usr/lib/llvm-14/lib/clang/14.0.0/include"

   "-internal-isystem"
   "/usr/local/include"

   "-internal-isystem"
   "/usr/bin/../lib/gcc/x86_64-linux-gnu/12/../../../../x86_64-linux-gnu/include"

   "-internal-externc-isystem" "/usr/include/x86_64-linux-gnu"
   "-internal-externc-isystem" "/include"
   "-internal-externc-isystem" "/usr/include"])

(comment
  (defn ^:private dump-api []
    (let [outf (fs/file (fs/canonicalize (io/resource "api/llama.edn")))]
      (fs/create-dirs (fs/parent outf))
      (with-open [w (io/writer outf)]
        (write-edn w
                   ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                    (str (fs/canonicalize "native/llama.cpp/llama.h"))
                    clang-args)))))
  (dump-api))
