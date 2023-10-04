(ns vortext.esther.util.native
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [babashka.process :refer [shell]]
   [babashka.fs :as fs]
   [clojure.java.io :as io])
  (:import
   java.nio.ByteBuffer
   com.sun.jna.Memory
   com.sun.jna.Pointer
   com.sun.jna.ptr.IntByReference
   com.sun.jna.ptr.FloatByReference
   com.sun.jna.Structure))

(defn ->bool [b]
  (if b
    (byte 1)
    (byte 0)))

(defn ->float-array-by-reference [v]
  (let [arr (float-array v)
        arrlen (alength arr)
        num-bytes (* arrlen 4)
        mem (doto (Memory. num-bytes)
              (.write 0 arr 0 arrlen))
        fbr (doto (FloatByReference.)
              (.setPointer mem))]
    fbr))

(defn ->int-array-by-reference [v]
  (let [arr (int-array v)
        arrlen (alength arr)
        num-bytes (* arrlen 4)
        mem (doto (Memory. num-bytes)
              (.write 0 arr 0 arrlen))
        ibr (doto (IntByReference.)
              (.setPointer mem))]
    ibr))


(def ptr->int-array #(.getIntArray % 0 (/ (.size %) Integer/BYTES)))

(defn int-array->memory
  [arr]
  (let [arrlen (alength arr)
        num-bytes (* arrlen Integer/BYTES)
        mem (Memory. num-bytes)]
    (dotimes [i arrlen]
      (.setInt mem (* i Integer/BYTES) (aget arr i)))
    mem))

(defn int-array->int-array-by-reference
  [arr]
  (let [ibr (doto (IntByReference.)
              (.setPointer (int-array->memory arr)))]
    ibr))

;; API generation
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


;; You can find this by calling clang -### empty-file.h
;; See https://github.com/phronmophobic/clong/blob/main/src/com/phronemophobic/clong/clang.clj#L27-L43
;; See https://github.com/phronmophobic/clong/tree/main#tips
#_(def clang-args
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

(defn remove-quotes [strings]
  (map #(clojure.string/replace % "\"" "") strings))

(defn get-clang-args
  []
  (let [tmp-h (fs/delete-on-exit
               (fs/create-temp-file {:suffix ".h"}))
        cmd (str/join " " ["clang" "-###" (str tmp-h)])
        result (-> (shell {:err :string} cmd) deref :err)
        result (str/split (last (str/split result #"\n")) #" ")]
    (remove-quotes
     (flatten (filter
               (fn [[k _v]] (or (str/includes? k "resource-dir")
                                (str/includes? k "isystem")))
               (partition 2 (rest result)))))))


(defn dump-api
  [header-path out-path]
  (let [outf (fs/file out-path)]
    (fs/create-dirs (fs/parent outf))
    (with-open [w (io/writer outf)]
      (write-edn w
                 ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                  (str header-path)
                  (get-clang-args))))))



(comment
  (dump-api (fs/canonicalize "native/llama.cpp/examples/grammar/grammar.h")
            (fs/canonicalize "resources/api/grammar.edn"))

  (dump-api (fs/canonicalize "native/llama.cpp/llama.h")
            (fs/canonicalize "resources/api/llama.edn")))
