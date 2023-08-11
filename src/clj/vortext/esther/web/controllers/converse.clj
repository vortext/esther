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

(defn get-context
  [request]
  (let [from-request (json/read-value
                      (get-in request [:params :context] "")
                      json/keyword-keys-object-mapper)]
    from-request))


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

(defn answer!
  [opts history request]
  (let [{:keys [params]} request
        context  (get-context request)
        request-with-context (assoc params :context context)
        _ (log/debug "request:" (pprint/pprint request-with-context))
        response (openai/complete history request-with-context)
        _ (log/debug "response" (pprint/pprint response))]
    (remember!
     opts
     {:response response
      :request request-with-context
      :ts (unix-ts)})))

(defn converse!
  [_opts request]
  (http-response/ok
   (answer!
    (get-in request [:session :history] [])
    request)))

;;; Scratch
(comment
  ;; (def query-fn (:db.sql/query-fn state/system))

  (query-fn :push-memory {:gid (rand 1000000)
                          :emoji ""
                          :prediction "?"
                          :question "!"
                          :summary "$"
                          :image_prompt "%"})

  (query-fn :random-memories {}))
