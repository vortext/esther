(ns vortext.esther.web.controllers.memory
  (:require
   [next.jdbc :as jdbc]
   [vortext.esther.secrets :as secrets]
   [vortext.esther.util :refer [random-base64]]))

(defn see-keyword
  [query-fn tx user kw]
  (let [{:keys [uid secret]} (:vault user)
        secret-kw (secrets/encrypt-for-sql {:value kw} secret)
        data {:uid uid :keyword secret-kw}]
    (query-fn tx :see-keyword data)))

(defn remember!
  ([opts user sid content]
   (remember! opts user sid content []))
  ([opts user sid content keywords]
   (let [{:keys [connection query-fn]} (:db opts)
         {:keys [uid secret]} (:vault user)
         gid (random-base64)
         memory {:gid gid
                 :uid uid
                 :sid sid
                 :content (secrets/encrypt-for-sql content secret)}]
     (jdbc/with-transaction [tx connection]
       (query-fn tx :push-memory memory)
       (doall
        (map (fn [kw] (see-keyword query-fn tx user kw)) keywords)))
     content)))

(defn construct-memories
  [user contents]
  (let [{:keys [_uid secret]} (:vault user)
        decrypt #(secrets/decrypt-from-sql % secret)]
    (map (comp decrypt :content) contents)))

(defn last-memories
  ([opts user]
   (last-memories opts user 10))
  ([opts user n]
   (let [{:keys [query-fn]} (:db opts)
         uid (get-in user [:vault :uid])]
     (construct-memories
      user
      (query-fn :last-n-memories {:uid uid :n n})))))
