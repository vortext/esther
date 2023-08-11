(ns vortext.esther.util.security
  (:require
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer [bytes->hex bytes->str bytes->b64-str]]
   [buddy.core.nonce :as nonce]))

(defn random-id
  ([] (random-id 64))
  ([l] (nonce/random-nonce l)))

(defn random-hex [] (bytes->hex (random-id)))

(defn random-base64
  ([] (random-base64 64))
  ([l] (bytes->b64-str (random-id l) true)))
