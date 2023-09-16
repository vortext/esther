(ns vortext.esther.util.crc32
  (:require
   [buddy.core.codecs :as codecs]
   [clojure.java.io :as io])
  (:import
   [java.util.zip CRC32]
   [java.nio.file Files Paths]))


(defn compute-crc32
  [s]
  (let [bytes (.getBytes s "UTF-8")
        crc32 (CRC32.)]
    (.update crc32 bytes)
    (.getValue crc32)))


(def checksum compute-crc32)


(defn compute-file-crc32
  [file-path]
  (let [crc32 (CRC32.)
        buffer (byte-array 4096)]
    (with-open [input-stream (io/input-stream file-path)]
      (loop []
        (let [read-bytes (.read input-stream buffer)]
          (when (not= read-bytes -1)
            (.update crc32 buffer 0 read-bytes)
            (recur)))))
    (.getValue crc32)))


(defn crc32->base64-str
  [crc32]
  (codecs/bytes->b64-str
   (codecs/long->bytes crc32) true)) ; true = websafe
