(ns vortext.esther.util
  (:require
   [clojure.tools.logging :as log]
   [jsonista.core :as json]

   [babashka.process :refer [shell]]
   [clojure.string :as str]
   [buddy.core.codecs :as codecs]
   [babashka.fs :as fs]
   [buddy.core.nonce :as nonce])
  (:import (java.util Base64)))

(def pretty-object-mapper
  (json/object-mapper
   {:pretty true}))

;; JSON utils
(defn pretty-json
  [obj]
  (json/write-value-as-string obj pretty-object-mapper))

(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))

(defn repair-json
  [maybe-json]
  (let [cmd "node_modules/jsonrepair/bin/cli.js"
        cmd (str (fs/canonicalize (fs/path cmd)))
        result (shell {:in maybe-json :cmd [cmd]
                       :err :string
                       :out :string})]
    (if (empty? (:out result))
      (throw (Exception. (:err result)))
      (:out result))))

(defn parse-maybe-json
  [maybe-json]
  (try
    (read-json-value maybe-json)
    (catch com.fasterxml.jackson.core.JsonParseException e
      (log/warn ["JSON Parsing Error at line " (.getLineNr (.getLocation e))
                 ", column " (.getColumnNr (.getLocation e))
                 ": " e maybe-json])
      (try
        (parse-maybe-json (repair-json maybe-json))
        (catch Exception _ maybe-json)))))

;; Base64
(defn bytes->b64 [^bytes b] (String. (.encode (Base64/getEncoder) b)))
(defn b64->bytes [^String s]
  (codecs/b64->bytes s))


;; Random
(defn random-id
  ([] (random-id 64))
  ([l] (nonce/random-nonce l)))

(defn random-hex [] (codecs/bytes->hex (random-id)))

(defn random-base64
  ([] (random-base64 64))
  ([l] (codecs/bytes->b64-str (random-id l) true)))

;; Newlines
(defn unescape-newlines [s]
  (str/replace s "\\n" "\n"))

(defn escape-newlines [s]
  (clojure.string/replace s "\n" "\\\\n"))

(defn strs-to-markdown-list [strs]
  (clojure.string/join "\n" (map #(str "- " %) strs)))
