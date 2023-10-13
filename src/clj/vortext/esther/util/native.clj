(ns vortext.esther.util.native
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [babashka.process :refer [shell]]
   [babashka.fs :as fs]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io])
  (:import
   com.sun.jna.Memory
   com.sun.jna.ptr.IntByReference
   com.sun.jna.ptr.FloatByReference))

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

(defn seq->memory
  [arr]
  (let [arrlen (count arr)
        type (class (first arr))
        type-name (.getName type)
        type-info
        {"java.lang.Integer"   [Integer/BYTES #(.setInt %1 %2 %3)]
         "java.lang.Byte"      [Byte/BYTES #(.setByte %1 %2 %3)]
         "java.lang.Float"     [Float/BYTES #(.setFloat %1 %2 %3)]
         "java.lang.Long"      [Long/BYTES #(.setLong %1 %2 %3)]
         "java.lang.Double"    [Double/BYTES #(.setLong %1 %2 %3)]
         "java.lang.Short"     [Short/BYTES #(.setLong %1 %2 %3)]
         "java.lang.Character" [Character/BYTES #(.setChar %1 %2 %3)]}
        [size set-fn] (get type-info type-name)
        _ (when-not size (throw (IllegalArgumentException.
                                 (format "Unsupported type: %s" type-name))))
        mem (doto (Memory. (* arrlen size)) (.clear))]
    (doseq [[i val] (map-indexed vector arr)]
      (set-fn mem (* i size) val))
    mem))


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
