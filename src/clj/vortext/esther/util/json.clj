(ns vortext.esther.util.json
  (:require
   [clojure.string :as str]
   [cognitect.transit :as transit]
   [jsonista.core :as json]
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [vortext.esther.util.polyglot :as polyglot]))

(def pretty-object-mapper
  (json/object-mapper
   {:pretty true}))

(defn pretty-json
  [obj]
  (json/write-value-as-string obj pretty-object-mapper))

(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))

(def write-value-as-string json/write-value-as-string) ;; alias

(def repair-json
  (let [script "scripts/jsonrepair/lib/umd/jsonrepair.js"
        script (str (fs/canonicalize (io/resource script)))

        jsonrepair (polyglot/js-api script "JSONRepair" [:jsonrepair])]
    (fn [args]
      ((:jsonrepair jsonrepair) args))))

(defn parse-repair-json
  [maybe-json]
  (try
    (read-json-value maybe-json)
    (catch com.fasterxml.jackson.core.JsonParseException _e
      (try
        (parse-repair-json (repair-json maybe-json))
        (catch Exception _ maybe-json)))))

;; Transit json

(defn write-transit-to-file [obj filename]
  (with-open [out (io/output-stream filename)]
    (transit/write (transit/writer out :json) obj)))

(defn read-transit-from-file [filename]
  (with-open [in (io/input-stream filename)]
    (transit/read (transit/reader in :json))))
