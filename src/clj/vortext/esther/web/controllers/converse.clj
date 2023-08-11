(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util.security :refer [random-base64]]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [vortext.esther.web.routes.utils :as utils]
   [ring.util.http-response :as http-response]))

(defn push-memory
  [history-size new-memory memories]
  (conj (vec (take-last history-size memories)) new-memory))

(defn read-json-value
  [str]
  (json/read-value str json/keyword-keys-object-mapper))

(defn get-context
  [memories request]
  (read-json-value
   (get-in request [:params :context] "")))


(defn remember!
  [opts answer]
  (let [;; {:keys [query-fn]} (utils/route-data request)
        {:keys [query-fn connection]} opts
        response (:response answer)
        memory-gid (random-base64)
        entry-gid (random-base64)
        memory {:gid memory-gid
                :emoji (:state response)
                :prediction (:prediction response)
                :question (:question response)
                :summary (:summary response)
                :image_prompt (:image-prompt response)}
        entry {:gid entry-gid
               :uid "<user here>"
               :content (json/write-value-as-string answer)
               :memory memory-gid}]
    (jdbc/with-transaction [tx connection]
      (query-fn tx :push-memory memory)
      (query-fn tx :push-entry entry))
    answer))

(defn parse-entries
  [entries]
  (map (comp read-json-value :content) entries))

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        last-10-entries (reverse ((:query-fn opts) :last-10-entries {}))
        memories (parse-entries last-10-entries)

        context  (get-context memories request)
        request-with-context (assoc params :context context)
        response (openai/complete
                  memories
                  request-with-context)]
    (remember!
     opts
     {:response response
      :request request-with-context
      :ts (unix-ts)})))

(defn converse!
  [_opts request]
  (http-response/ok
   {:todo "FIXME"}))

;;; Scratch
