(ns vortext.esther.common
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]))


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


(defn unescape-newlines
  [s]
  (str/replace s "\\n" "\n"))


(defn escape-newlines
  [s]
  (str/replace s "\n" "\\\\n"))


(defn parse-number
  [s]
  (if (number? s) s
      (try (Long/parseLong s)
           (catch Exception _  nil))))
