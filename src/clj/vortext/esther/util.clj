(ns vortext.esther.util
  (:require
   [clojure.tools.logging :as log]
   [buddy.core.codecs :as codecs]
   [buddy.core.nonce :as nonce])
  (:import
   (java.util Base64)))

;; Base64
(defn bytes->b64 [^bytes b] (String. (.encode (Base64/getEncoder) b)))
(defn b64->bytes [^String s]
  (codecs/b64->bytes s))

;; Random
(defn random-id
  ([] (random-id 64))
  ([l] (nonce/random-nonce l)))

(defn random-hex [] (codecs/bytes->hex (random-id)))

(defn random-base64
  ([] (random-base64 64))
  ([l] (codecs/bytes->b64-str (random-id l) true)))
