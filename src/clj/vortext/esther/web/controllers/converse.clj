(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [camel-snake-kebab.core :as csk]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util.security :refer [random-base64]]
   [vortext.esther.util :refer [read-json-value]]
   [clojure.string :as str]
   [jsonista.core :as json]
   [clojure.tools.logging :as log]
   ;; [clojure.pprint :as pprint]
   [ring.util.http-response :as http-response]))


(defn get-context
  [request]
  (read-json-value
   (get-in request [:params :context] "")))


(defn remember!
  [opts answer]
  (let [ ;; {:keys [query-fn]} (utils/route-data request)
        {:keys [query-fn]} opts
        response (:response answer)
        memory-gid (random-base64)
        keywords (map csk/->kebab-case (get response :keywords []))
        memory {:gid memory-gid
                :emoji (:state response)
                :prediction (:prediction response)
                :question (:question response)
                :content (json/write-value-as-string answer)
                :keywords  (str/join "," keywords)
                :image_prompt (:image-prompt response)}]
    (query-fn :push-memory memory)
    answer))

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        last-10-memories (reverse ((:query-fn opts) :last-10-memories {}))
        context (get-context request)
        request-with-context (assoc params :context context)
        response (openai/complete
                  last-10-memories
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
