(ns vortext.esther.util
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [babashka.process :refer [shell]]
   [buddy.core.codecs :as codecs]
   [buddy.core.nonce :as nonce]))

;; Base64
(def bytes->b64 codecs/bytes->b64)
(def b64->bytes codecs/b64->bytes)

;; Random
(defn random-id
  ([] (random-id 64))
  ([l] (nonce/random-nonce l)))

(defn random-hex [] (codecs/bytes->hex (random-id)))

(defn random-base64
  ([] (random-base64 64))
  ([l] (codecs/bytes->b64-str (random-id l) true)))

(def md5sums
  (let [shell-fn (partial shell {:out :string} "md5sum")
        parse-row (comp vec reverse #(str/split % #"  "))]
    (fn [paths]
      (let [results (-> (apply shell-fn paths) deref :out)]
        (-> (map parse-row (str/split results #"\n"))
            (into {}))))))
