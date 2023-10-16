(ns vortext.esther.util.jna
  (:require
   [clojure.tools.logging :as log])
  (:import
   com.sun.jna.Memory
   (com.sun.jna.ptr FloatByReference IntByReference)))


(defn ->bool
  [b]
  (if b
    (byte 1)
    (byte 0)))


(defn ->float-array-by-reference
  [v]
  (let [arr (float-array v)
        arrlen (alength arr)
        num-bytes (* arrlen 4)
        mem (doto (Memory. num-bytes)
              (.write 0 arr 0 arrlen))
        fbr (doto (FloatByReference.)
              (.setPointer mem))]
    fbr))


(defn ->int-array-by-reference
  [v]
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
