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
  (:gen-class))

(def h "zlib.h")
(def api-file (str (fs/canonicalize (fs/path (io/resource "api") "zlib.json"))))
(def api-def (read-transit-from-file api-file))

(def libz (com.sun.jna.NativeLibrary/getInstance "z"))

(gen/def-api libz api-def)

(def buffer-size 2048) ; Adjust buffer size as needed

(defn update-crc32
  [crc buffer read-count]
  ;; Call the crc32 function with the current crc value, buffer, and read-count to get the new crc value
  (crc32 crc buffer read-count))

(defn calculate-crc32
  [file-path]
  (let [file-input-stream (io/input-stream file-path)
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

;;; Scratch
(comment
  (defn write-api ;; I used this to write the API
    [h out]
    (let [h-file (str (fs/canonicalize (io/resource (str "h/" h))))
          api (clong/easy-api h-file)]
      (write-transit-to-file api (str out)))))
