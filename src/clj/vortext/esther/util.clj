(ns vortext.esther.util
  (:require [jsonista.core :as json]))

(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))
