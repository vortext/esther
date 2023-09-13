(ns vortext.esther.web.controllers.users
  (:require
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [buddy.hashers :as hashers]
   [buddy.core.nonce :as nonce]
   [buddy.core.hash :as hash]
   [buddy.core.codecs :as codecs]
   [vortext.esther.util.json :as json]
   [vortext.esther.secrets :as secrets]))


(defn build-vault
  [username password]
  (let [uid (hash/sha256 username)
        secret (secrets/stretched-b64-str password)
        vault {:uid uid
               :secret secret}]
    (secrets/encrypt-for-sql vault secret)))

(defn insert!
  [{:keys [db]} username password]
  (let [query-fn (:query-fn db)
        {:keys [data iv]} (build-vault username password)]
    (query-fn
     :create-user
     {:username username
      :password_hash (hashers/encrypt password)
      :vault_data data
      :vault_iv iv})))

(defn find-by-username
  [{:keys [db]} username]
  ((:query-fn db)
   :find-user-by-username {:username username}))

(defn retrieve
  [opts username password]
  (when-let [user (find-by-username opts username)]
    (when (and username password (hashers/check password (:password_hash user)))
      (let [{:keys [:vault_iv :vault_data]} user
            encrypted-vault {:iv vault_iv :data vault_data}
            secret (secrets/stretched-b64-str password)
            vault (secrets/decrypt-from-sql encrypted-vault secret)]
        (-> user
            (assoc :vault vault)
            (dissoc :vault_iv :vault_data))))))

(defmethod ig/init-key :users/ensure-test-user
  [_ opts]
  (let [username "test"
        password "test"]
    (when-not (find-by-username opts username)
      (do
        (log/warn "creating user " username " with password " password)
        (insert! opts username password)))))
