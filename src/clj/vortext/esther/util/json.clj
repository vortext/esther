(ns vortext.esther.util.json
  (:require
    [clojure.java.io :as io]
    [cognitect.transit :as transit]
    [jsonista.core :as json]))


(def pretty-object-mapper
  (json/object-mapper
    {:pretty true}))


(defn pretty-json
  [obj]
  (json/write-value-as-string obj pretty-object-mapper))


(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))


(def write-value-as-string json/write-value-as-string) ; alias

;; Transit json

(defn write-transit-to-file
  [obj filename]
  (with-open [out (io/output-stream filename)]
    (transit/write (transit/writer out :json) obj)))


(defn read-transit-from-file
  [filename]
  (with-open [in (io/input-stream filename)]
    (transit/read (transit/reader in :json))))
