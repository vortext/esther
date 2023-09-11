(ns vortext.esther.util.zlib
  (:refer-clojure :exclude [read sync]) ;; !!!
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [buddy.core.codecs :as codecs]
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

(defn update-crc32
  [crc buffer read-count]
  ;; Call the crc32 function with the current crc value, buffer, and read-count to get the new crc value
  (crc32 crc buffer read-count))

(defn calculate-crc32
  [file-path]
  (let [buffer-size 2048
        file-input-stream (io/input-stream file-path)
        buffer (byte-array buffer-size)
        crc-ref (atom 0)]
    (try
      (loop []
        (let [read-count (.read file-input-stream buffer)]
          (when (pos? read-count)
            (swap! crc-ref update-crc32 buffer read-count)
            (recur))))
      (finally
        (.close file-input-stream)))
    @crc-ref))


(defn text->crc32
  [text]
  (let [bytes (.getBytes text)]
    (crc32 0 bytes (count bytes))))

(defn crc32->base64-str
  [crc32]
  (codecs/bytes->b64-str
   (codecs/long->bytes crc32) true))

(defn compress-str
  [source]
  (let [source-bytes (.getBytes source "UTF-8")
        dest (byte-array (* 2 (count source-bytes)))  ;; Allocate a buffer based on the source length
        dest-size* (doto (LongByReference.)
                     (.setValue (alength dest)))
        ret (compress dest dest-size* source-bytes (count source-bytes))
        data (byte-array (subvec (vec dest) 0 (.getValue dest-size*)))]
    (if (zero? ret)
      (str (codecs/bytes->b64-str data) " " (count source-bytes))
      (throw (Exception. (str "Compression failed with error code: " ret))))))

(defn decompress
  [compressed]
  (let [[data size] (str/split compressed #" ")
        uncompressed-length (Long/parseLong size)
        dest (byte-array uncompressed-length)
        dest-size* (doto (LongByReference.)
                     (.setValue uncompressed-length))
        ret (uncompress dest dest-size* (codecs/b64->bytes data) (count data))]
    (if (zero? ret)
      (String. dest "UTF-8")
      (throw (Exception. (str "Decompression failed with error code: " ret))))))

;;; Scratch
(comment
  (decompress (compress-str "test"))

  (defn write-api ;; I used this to write the API
    [h out]
    (let [h-file (str (fs/canonicalize (io/resource (str "h/" h))))
          api (clong/easy-api h-file)]
      (write-transit-to-file api (str out)))))
