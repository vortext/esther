(ns vortext.esther.common
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]))

(defn parse-number
  [s]
  (when (re-find #"^-?\d+\.?\d*$" s)
    (read-string s)))

(defn update-value
  "Updates the given key in the given map. Uses the given function to transform the value, if needed."
  [key transform-fn m default-value]
  (let [value (get m key)
        transformed-value (transform-fn value)]
    (assoc m key (if transformed-value
                   transformed-value
                   (or (transform-fn (str value))
                       default-value)))))

(defn namespace-keywordize-map
  [obj]
  (into
   #{}
   (keep (fn [[k v]]
           (csk/->kebab-case
            (str (name k) ":" (str/trim (str/lower-case  (str v)))))) obj)))
