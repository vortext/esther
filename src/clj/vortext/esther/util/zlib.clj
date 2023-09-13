(ns vortext.esther.util.zlib
  (:require
   [buddy.core.codecs :as codecs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [vortext.esther.util.raw.zlib :as raw])
  (:import
   (com.sun.jna.ptr
    LongByReference)))


(defn update-crc32
  [crc buffer read-count]
  ;; Call the crc32 function with the current crc value, buffer, and read-count to get the new crc value
  (raw/crc32 crc buffer read-count))


(defn calculate-crc32
  [file-path]
  (let [buffer-size 2048
        file-input-stream (io/input-stream file-path)
        buffer (byte-array buffer-size)  ; Buffer created here
        crc-ref (atom 0)]
    (try
      (loop []
        (let [read-count (.read file-input-stream buffer)]  ; Buffer reused here
          (when (pos? read-count)
            (swap! crc-ref update-crc32 buffer read-count)
            (recur))))
      (finally
        (.close file-input-stream)))
    @crc-ref))


(defn text->crc32
  [text]
  (let [bytes (.getBytes text)]
    (raw/crc32 0 bytes (count bytes))))


(defn crc32->base64-str
  [crc32]
  (codecs/bytes->b64-str
    (codecs/long->bytes crc32) true)) ; true = websafe

(defn checksum
  [text]
  (-> text
      (text->crc32)
      (crc32->base64-str)))


(defn compress
  [source]
  (let [source-bytes (.getBytes source "UTF-8")
        initial-buffer-size (+ 12 (int (* 1.001 (count source-bytes))))
        dest (byte-array initial-buffer-size)
        dest-size* (doto (LongByReference.)
                     (.setValue (alength dest)))
        ret (raw/compress dest dest-size* source-bytes (count source-bytes))
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
        ret (raw/uncompress dest dest-size* (codecs/b64->bytes data) (count data))]
    (if (zero? ret)
      (String. dest "UTF-8")
      (throw (Exception. (str "Decompression failed with error code: " ret))))))


;; As tempting as it is to implement gzip here, you're better off using the shell for that.
;; Or the jvm impl, I have no idea why this exists other than out of curiousity.

;; Scratch
(comment
  (decompress (compress "test")))
