(ns vortext.esther.util
  (:require
   [clojure.tools.logging :as log]
   [buddy.core.codecs :as codecs]
   [buddy.core.nonce :as nonce]))

;; Random
(defn random-id
  ([] (random-id 64))
  ([l] (nonce/random-nonce l)))

(defn random-hex [] (codecs/bytes->hex (random-id)))

(defn random-base64
  ([] (random-base64 64))
  ([l] (codecs/bytes->b64-str (random-id l) true)))
