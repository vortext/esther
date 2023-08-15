(ns vortext.esther.web.controllers.memory
  (:require
   [next.jdbc :as jdbc]
   [vortext.esther.secrets :as secrets]
   [vortext.esther.util :refer [random-base64]]))

(defn see-keyword
  [query-fn tx user kw]
  (let [{:keys [uid secret]} (:vault user)
        {:keys [data iv]} (secrets/encrypt-for-sql kw secret)]
    (query-fn tx :see-keyword {:uid uid :data data :iv iv})))

(defn remember!
  ([opts user sid content]
   (remember! opts user sid content []))
  ([opts user sid content keywords]
   (let [{:keys [connection query-fn]} (:db opts)
         {:keys [uid secret]} (:vault user)
         gid (random-base64)
         {:keys [data iv]} (secrets/encrypt-for-sql content secret)
         memory {:gid gid
                 :uid uid
                 :sid sid
                 :data data
                 :iv iv}]
     (jdbc/with-transaction [tx connection]
       (query-fn tx :push-memory memory)
       (doall
        (map (fn [kw] (see-keyword query-fn tx user kw)) keywords)))
     content)))

(defn construct-memories
  [user contents]
  (let [{:keys [_uid secret]} (:vault user)
        decrypt #(secrets/decrypt-from-sql % secret)]
    (map decrypt contents)))

(defn last-memories
  ([opts user]
   (last-memories opts user 10))
  ([opts user n]
   (let [{:keys [query-fn]} (:db opts)
         uid (get-in user [:vault :uid])]
     (construct-memories
      user
      (query-fn :last-n-memories {:uid uid :n n})))))
