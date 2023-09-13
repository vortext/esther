(ns vortext.esther.secrets
  "Utilities to for encrypting credentials,
  storing them on disk, and editing them.

  See https://gist.github.com/matthewdowney/d5d816a0274ea2d1fd5e9eab4a933e57
  https://matthewdowney.github.io/encrypting-keys-in-clojure-applications.html"
  (:require
    [babashka.fs :as fs]
    [buddy.core.codecs :as codecs]
    [buddy.core.crypto :as crypto]
    [buddy.core.kdf :as kdf]
    [buddy.core.nonce :as nonce]
    [clojure.edn :as edn]
    [clojure.tools.logging :as log]
    [vortext.esther.util.json :as json]))


(defonce secrets
  (edn/read-string
    (slurp (str (fs/expand-home "~/.secrets.edn")))))


(def algorithm :aes256-cbc-hmac-sha512)
(def n-bytes 64)


;; Take a weak text passphrase and make it brute force resistant
(defn slow-key-stretch-with-pbkdf2
  [weak-text-key]
  (kdf/get-bytes
    (kdf/engine
      {:key weak-text-key
       ;; Keep this constant across runs
       :salt (codecs/b64->bytes (:salt secrets))
       :alg :pbkdf2
       :digest :sha512
       ;; Target O(100ms) on commodity hardware
       :iterations 1e5})
    n-bytes))


(defn encrypt
  "Encrypt and return a {:data <b64>, :iv <b64>} that can be decrypted with the
  same `password`."
  [clear-text password]
  (let [initialization-vector (nonce/random-nonce 16)]
    {:data (crypto/encrypt
             (codecs/to-bytes clear-text)
             password
             initialization-vector
             {:algorithm algorithm})
     :iv initialization-vector}))


(defn decrypt
  "Decrypt and return the clear text for some output of `encrypt` given the
  same `password` used during encryption."
  [{:keys [data iv]} password]
  (codecs/bytes->str
    (crypto/decrypt data password iv {:algorithm algorithm})))


(def stretched-b64-str #(-> % slow-key-stretch-with-pbkdf2 codecs/bytes->b64-str))


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
