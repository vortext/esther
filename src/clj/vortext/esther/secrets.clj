(ns vortext.esther.secrets
  "Utilities to for encrypting stuff.

  See https://gist.github.com/matthewdowney/d5d816a0274ea2d1fd5e9eab4a933e57
  https://matthewdowney.github.io/encrypting-keys-in-clojure-applications.html"
  (:require
   [babashka.fs :as fs]
   [buddy.core.codecs :as codecs]
   [buddy.core.kdf :as kdf]
   [clojure.edn :as edn]
   [caesium.crypto.secretbox :as sb]
   [caesium.crypto.pwhash :as pwhash]
   [caesium.randombytes :as rb]
   [caesium.byte-bufs :as bb]
   [caesium.util :as u]
   [caesium.crypto.secretbox :as sb]
   [clojure.tools.logging :as log]
   [vortext.esther.util.json :as json]))


(defonce secrets
  (edn/read-string
   (slurp (str (fs/expand-home "~/.secrets.edn")))))


;; helper function for creating salts from integers. may be useful for deterministic
;; key derivation, incrementing subkeys from 0.
(def int->salt (partial u/n->bytes pwhash/saltbytes))


(defn derive-key
  [weak-text-key]
  (pwhash/pwhash
   64 ;; n-bytes
   weak-text-key
   (codecs/b64->bytes (:salt secrets))
   pwhash/opslimit-sensitive
   pwhash/memlimit-sensitive
   pwhash/alg-default))


(def derive-key-base64-str #(-> % derive-key codecs/bytes->b64-str))


(defn encrypt
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`."
  [clear-text password]
  (let [initialization-vector (sb/int->nonce 16)]
    {:data (sb/encrypt
            password initialization-vector
            (codecs/to-bytes clear-text))
     :iv initialization-vector}))


(defn decrypt
  "Decrypt and return the clear text for some output of `encrypt` given the
  same `password` used during encryption."
  [{:keys [data iv]} password]
  (codecs/bytes->str
   (sb/decrypt password iv data)))



(defn decrypt-from-sql
  [content password]
  (-> content
      (decrypt (codecs/b64->bytes password))
      (json/read-json-value)))


(defn encrypt-for-sql
  [content password]
  (-> content
      (json/write-value-as-string)
      (encrypt (codecs/b64->bytes password))))


(defn password-hash
  [password]
  (pwhash/pwhash-str password
                     pwhash/opslimit-sensitive
                     pwhash/memlimit-sensitive))


(def check pwhash/pwhash-str-verify)
