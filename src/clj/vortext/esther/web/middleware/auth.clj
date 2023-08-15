(ns vortext.esther.web.middleware.auth
  (:require
   [clojure.tools.logging :as log]
   [buddy.hashers :as hashers]
   [buddy.core.hash :as hash]
   [vortext.esther.util :refer [read-json-value bytes->b64]]
   [vortext.esther.secrets :as secrets]
   [jsonista.core :as json]
   [buddy.auth.backends :refer [session]]
   [buddy.auth.accessrules :refer [error]]))

;; Create an instance of auth backend.
(def auth-backend (session))

(defn read-vault
  [user password]
  (let [encrypted-vault
        (read-json-value (:vault user))

        decrypted-vault
        (read-json-value (secrets/decrypt encrypted-vault password))]
    (assoc user :vault decrypted-vault)))

(defn write-vault
  [{:keys [vault] :as user}]
  (let [encrypt (fn [s] (secrets/encrypt s (:secret vault)))
        encrypted (encrypt (json/write-value-as-string vault))]
    (assoc user :vault (json/write-value-as-string encrypted))))

(defn insert-user!
  [{:keys [db]} username password]
  (let [query-fn (:query-fn db)
        uid (-> (hash/sha256 username) (bytes->b64))
        stretched-password (secrets/slow-key-stretch-with-pbkdf2 password 64)
        vault {:uid uid
               :secret stretched-password}
        user (write-vault
              {:username username
               :password_hash (hashers/encrypt password)
               :vault vault})]
    (when (nil? (query-fn :find-user-by-username {:username username}))
      (query-fn :create-user user))))

(defn authenticate
  "Checks if request (with username/password :query-params)
  or username/password is valid"
  ([opts request]
   (let [username (get-in request [:params :username])
         password (get-in request [:params :password])]
     (authenticate opts username password)))
  ([opts username password]
   (let [query-fn (get-in opts [:db :query-fn])
         user (query-fn :find-user-by-username {:username username})]
     (if (and username password (hashers/check password (:password_hash user)))
       (read-vault user (secrets/slow-key-stretch-with-pbkdf2 password 64)) nil))))


;; Access Level Handlers
(defn authenticated? [request]
  (some? (:identity (:session request))))


(defn authenticated-access
  "Check if request coming in is authenticated with user/password "
  [request]
  (let [valid? (authenticated? request)]
    (if valid? true (error "unauthenticated"))))
