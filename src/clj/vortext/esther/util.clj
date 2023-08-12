(ns vortext.esther.util
  (:require
   [clojure.tools.logging :as log]
   [jsonista.core :as json]))

(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))

(defn parse-maybe-json
  [maybe-json]
  (try
    (json/read-value maybe-json json/keyword-keys-object-mapper)
    (catch com.fasterxml.jackson.core.JsonParseException e
      (log/warn ["JSON Parsing Error at line " (.getLineNr (.getLocation e))
                 ", column " (.getColumnNr (.getLocation e))
                 ": " e maybe-json])
      nil)))
