(ns vortext.esther.web.controllers.memory
  (:require
   [next.jdbc :as jdbc]
   [jsonista.core :as json]
   [vortext.esther.util :refer [read-json-value]]
   [vortext.esther.util.security :refer [random-base64]]))

(defn remember!
  ([opts uid sid content]
   (remember! opts uid sid content []))
  ([opts uid sid content keywords]
   (let [{:keys [connection query-fn]} (:db opts)
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

(defn contents-as-memories
  [jsons]
  (map (comp read-json-value :content) jsons))

(defn last-memories
  ([opts uid]
   (last-memories opts uid 10))
  ([opts uid n]
   (let [{:keys [query-fn]} (:db opts)]
     (contents-as-memories
      (query-fn :last-n-memories {:uid uid :n n})))))
