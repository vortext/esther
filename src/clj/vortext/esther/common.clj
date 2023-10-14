(ns vortext.esther.common
  (:require
    [babashka.fs :as fs]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]))


(defn read-resource
  [resource-path]
  (-> resource-path
      io/resource
      fs/canonicalize
      str
      slurp))


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
