(ns vortext.esther.secrets
  "Utilities to for encrypting credentials,
  storing them on disk, and editing them.

  See https://gist.github.com/matthewdowney/d5d816a0274ea2d1fd5e9eab4a933e57
  https://matthewdowney.github.io/encrypting-keys-in-clojure-applications.html"
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.core.crypto :as crypto]
            [buddy.core.kdf :as kdf]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [babashka.fs :as fs]
            [clojure.edn :as edn])
  (:import (java.util Base64)))

(def secrets
  (memoize
   (fn []
     (edn/read-string
      (slurp
       (str
        (fs/expand-home (fs/path "~/.secrets.edn"))))))))

(defn bytes->b64 [^bytes b] (String. (.encode (Base64/getEncoder) b)))
(defn b64->bytes [^String s] (.decode (Base64/getDecoder) (.getBytes s)))


(defn slow-key-stretch-with-pbkdf2 [weak-text-key n-bytes]
  (kdf/get-bytes
   (kdf/engine {:key weak-text-key
                :salt (b64->bytes (:salt (secrets)))
                :alg :pbkdf2
                :digest :sha512
                :iterations 1e5}) ;; target O(100ms) on commodity hardware
   n-bytes))

;; (slow-key-stretch-with-pbkdf2 password 64)

(defn encrypt
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`.

  Performs pbkdf2 key stretching with quite a few iterations on `password`."
  [clear-text password]
  (let [initialization-vector (nonce/random-bytes 16)]
    {:data (bytes->b64
            (crypto/encrypt
             (codecs/to-bytes clear-text)
             password
             initialization-vector
             {:algorithm :aes256-cbc-hmac-sha512}))
     :iv (bytes->b64 initialization-vector)}))


(defn decrypt
  "Decrypt and return the clear text for some output of `encrypt` given the
  same `password` used during encryption."
  [{:keys [data iv]} password]
  (codecs/bytes->str
   (crypto/decrypt
    (b64->bytes data)
    password
    (b64->bytes iv)
    {:algorithm :aes256-cbc-hmac-sha512})))
