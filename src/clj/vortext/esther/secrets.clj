(ns vortext.esther.secrets
  "Utilities to for encrypting stuff.
   https://doc.libsodium.org/
   https://github.com/lvh/caesium"
  (:require
   [babashka.fs :as fs]
   [buddy.core.codecs :as codecs]
   [caesium.crypto.pwhash :as pwhash]
   [caesium.crypto.secretbox :as sb]
   [caesium.randombytes :as rb]
   [caesium.util :as u]
   [clojure.edn :as edn]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg]))


(defonce secrets
  (edn/read-string
   (slurp (str (fs/expand-home "~/.secrets.edn")))))


;; helper function for creating salts from integers. may be useful for deterministic
;; key derivation, incrementing subkeys from 0.
(def int->salt (partial u/n->bytes pwhash/saltbytes))


(defn derive-key
  [weak-text-key]
  (pwhash/pwhash
   sb/keybytes
   weak-text-key
   (codecs/b64->bytes (:salt secrets))
   pwhash/opslimit-sensitive
   pwhash/memlimit-sensitive
   pwhash/alg-default))


(def derive-key-base64-str #(-> % derive-key codecs/bytes->b64-str))
(def random-base64 #(-> (rb/randombytes %) codecs/bytes->b64-str))

(def check pwhash/pwhash-str-verify)

(defn encrypt
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`."
  [clear-bytes password]
  (let [initialization-vector (sb/int->nonce 16)]
    {:data (sb/encrypt
            password initialization-vector
            clear-bytes)
     :iv initialization-vector}))


(defn decrypt
  "Decrypt and return the clear text for some output of `encrypt` given the
  same `password` used during encryption."
  [{:keys [data iv]} password]
  (sb/decrypt password iv data))


(defn decrypt-from-sql
  [content password]
  (-> content
      (decrypt (codecs/b64->bytes password))
      msg/unpack))


(defn encrypt-for-sql
  [content password]
  (-> content
      msg/pack
      (encrypt (codecs/b64->bytes password))))


(defn password-hash
  [password]
  (pwhash/pwhash-str
   password
   pwhash/opslimit-sensitive
   pwhash/memlimit-sensitive))
