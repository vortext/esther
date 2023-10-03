(ns vortext.esther.common
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]))


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



(defn split-first-word
  [s]
  (let [[_ first-word rest] (re-matches #"(\S+)\s*(.*)" s)]
    [first-word (or rest "")]))


(defn request-msg
  [obj]
  (-> obj :memory/events first :event/content :message))


(defn remove-namespaces
  [m]
  (let [remove-namespace
        (fn [k]
          (if (namespace k)
            (keyword (name k))
            k))]
    (walk/postwalk
     (fn [x]
       (if (map? x)
         (into {} (map (fn [[k v]] [(remove-namespace k) v]) x))
         x))
     m)))


(defn unescape-newlines [s]
  (str/replace s "\\n" "\n"))

(defn escape-newlines [s]
  (clojure.string/replace s "\n" "\\\\n"))
