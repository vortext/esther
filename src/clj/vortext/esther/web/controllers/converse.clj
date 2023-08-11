(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.ai.openai :as openai]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [jsonista.core :as json]
   [clojure.tools.logging :as log]
   [ring.util.http-response :as http-response]))

(defn get-context
  [request]
  (let [from-request (json/read-value
                      (get-in request [:params :context] "")
                      json/keyword-keys-object-mapper)]
    from-request))

(defn answer
  [history request]
  (let [{:keys [params]} request
        context  (get-context request)
        request-with-context (assoc params :context context)
        _ (log/debug "request:" (pprint/pprint request-with-context))
        response (openai/complete history request-with-context)
        _ (log/debug "response" (pprint/pprint response))]
    {:response response
     :request request-with-context
     :ts (unix-ts)}))

(defn converse!
  [request]
  (http-response/ok
   (answer
    (get-in request [:session :history] [])
    request)))
