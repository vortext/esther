(ns vortext.esther.secrets
  "Utilities to for encrypting credentials,
  storing them on disk, and editing them.

  See https://gist.github.com/matthewdowney/d5d816a0274ea2d1fd5e9eab4a933e57
  https://matthewdowney.github.io/encrypting-keys-in-clojure-applications.html"
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.core.crypto :as crypto]
            [buddy.core.kdf :as kdf]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [jsonista.core :as json]
            [vortext.esther.util :refer [read-json-value bytes->b64 b64->bytes]])
  (:import (java.util Base64)))

(def secrets
  (memoize
   (fn []
     (edn/read-string
      (slurp
       (str
        (fs/expand-home (fs/path "~/.secrets.edn"))))))))


(def algorithm :aes256-cbc-hmac-sha512)

;; Take a weak text passphrase and make it brute force resistant
(defn slow-key-stretch-with-pbkdf2 [weak-text-key n-bytes]
  (kdf/get-bytes
   (kdf/engine
    {:key weak-text-key
     ;; Keep this constant across runs
     :salt (b64->bytes (get :salt (secrets) "salt"))
     :alg :pbkdf2
     :digest :sha512
     ;; Target O(100ms) on commodity hardware
     :iterations 1e5})
   n-bytes))

;; (slow-key-stretch-with-pbkdf2 password 64)

(defn encrypt
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`."
  [clear-text password]
  (let [initialization-vector (nonce/random-bytes 16)]
    {:data (bytes->b64
            (crypto/encrypt
             (codecs/to-bytes clear-text)
             password
             initialization-vector
             {:algorithm algorithm}))
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
    {:algorithm algorithm})))


(defn decrypt-from-sql
  [content password]
  (read-json-value
   (decrypt (read-json-value content) (b64->bytes password))))

(defn encrypt-for-sql
  [content password]
  (json/write-value-as-string
   (encrypt (json/write-value-as-string content) (b64->bytes password))))
