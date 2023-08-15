(ns vortext.esther.web.controllers.memory
  (:require
   [next.jdbc :as jdbc]
   [jsonista.core :as json]
   [vortext.esther.util :refer [read-json-value]]
   [vortext.esther.util.security :refer [random-base64]]))

(defn remember!
  ([opts user sid content]
   (remember! opts user sid content []))
  ([opts user sid content keywords]
   (let [{:keys [connection query-fn]} (:db opts)
         uid (get-in user [:vault :uid])
         gid (random-base64)
         memory {:gid gid
                 :uid uid
                 :sid sid
                 :content (json/write-value-as-string content)}]
     (jdbc/with-transaction [tx connection]
       (query-fn tx :push-memory memory)
       (doall
        (map (fn [kw] (query-fn tx :see-keyword {:uid uid :keyword kw})) keywords)))
     content)))

(defn construct-memories
  [user contents]
  (map (comp read-json-value :content) contents))

(defn last-memories
  ([opts user]
   (last-memories opts user 10))
  ([opts user n]
   (let [{:keys [query-fn]} (:db opts)
         uid (get-in user [:vault :uid])]
     (construct-memories
      user
      (query-fn :last-n-memories {:uid uid :n n})))))
