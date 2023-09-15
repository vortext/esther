(ns vortext.esther.web.controllers.users
  (:require
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [vortext.esther.secrets :as secrets])
  (:import (java.util UUID)))

(def uid #(str (UUID/randomUUID)))

(defn build-vault
  [password]
  (let [vault {:uid (uid)
               :secret (secrets/random-base64 32)}]
    (secrets/encrypt-for-sql vault (secrets/derive-key-base64-str password))))


(defn insert!
  [{:keys [db]} username password]
  (let [query-fn (:query-fn db)
        {:keys [data iv]} (build-vault password)]
    (query-fn
     :create-user
     {:username username
      :password_hash (secrets/password-hash password)
      :data data
      :iv iv})))


(defn find-by-username
  [{:keys [db]} username]
  ((:query-fn db)
   :find-user-by-username {:username username}))


(defn retrieve
  [opts username password]
  (when-let [user (find-by-username opts username)]
    (when (and username password (secrets/check (:password_hash user) password))
      (let [encrypted-vault (select-keys user [:data :iv])
            vault (secrets/decrypt-from-sql
                   encrypted-vault
                   (secrets/derive-key-base64-str password))]
        (-> user
            (dissoc :data :iv)
            (assoc :vault vault))))))


(defmethod ig/init-key :users/ensure-test-user
  [_ opts]
  (let [username "test"
        password "test"]
    (when-not (find-by-username opts username)
      (log/warn "creating user " username "with password" password)
      (insert! opts username password))))
