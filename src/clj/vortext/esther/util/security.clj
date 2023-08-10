(ns vortext.esther.util.security
  (:require
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer [bytes->hex bytes->str bytes->b64-str]]
   [buddy.core.nonce :as nonce]))

(defn random-id [] (nonce/random-nonce 64))

(defn random-hex [] (bytes->hex (random-id)))

(defn random-base64 []
  (bytes->b64-str (random-id) true))
