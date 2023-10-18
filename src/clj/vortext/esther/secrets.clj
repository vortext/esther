(ns vortext.esther.secrets
  "Utilities for encrypting and decrypting secrets.
   https://doc.libsodium.org/
   https://github.com/lvh/caesium"
  (:require
   [clojure.tools.logging :as log]
   [babashka.fs :as fs]
   [buddy.core.codecs :as codecs]
   [caesium.crypto.pwhash :as pwhash]
   [caesium.crypto.secretbox :as sb]
   [caesium.randombytes :as rb]
   [msgpack.clojure-extensions]
   [msgpack.core :as msg]))

(def random-base64 #(-> (rb/randombytes %) codecs/bytes->b64-str))

(def salt
  (let [salt-file (str (fs/canonicalize "./.salt"))]
    (if (fs/exists? salt-file)
      (slurp salt-file)
      (let [new-salt (random-base64 12)]
        (log/warn "Generating " salt-file "with salt" new-salt
                  ". Keep the salt safe, lose it and nobody can login!")
        (spit salt-file new-salt)
        new-salt))))


(defn password-hash
  [password]
  (pwhash/pwhash-str
   password
   pwhash/opslimit-sensitive
   pwhash/memlimit-sensitive))


(defn derive-key
  [weak-text-key]
  (pwhash/pwhash
   sb/keybytes
   weak-text-key
   (codecs/b64->bytes salt)
   pwhash/opslimit-sensitive
   pwhash/memlimit-sensitive
   pwhash/alg-default))


(def derive-key-base64-str #(-> % derive-key codecs/bytes->b64-str))

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
