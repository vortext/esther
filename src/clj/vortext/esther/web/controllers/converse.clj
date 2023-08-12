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
        keywords (map (comp csk/->kebab-case str/trim) (get response :keywords []))
        memory {:gid memory-gid
                :uid "<user>"
                :sid (:sid answer)
                :emoji (:emoji response)
                :content (json/write-value-as-string answer)
                :keywords (when (seq keywords) (str/join "," keywords))
                :image_prompt (:image-prompt response)}]
    (query-fn :push-memory memory)
    answer))

(defn converse!
  [opts base-response]
  (let [{:keys [query-fn]} opts
        last-10-memories (reverse (query-fn :last-10-memories {}))
        response (openai/complete
                  last-10-memories
                  (:request base-response))
        response (assoc base-response :response response)]
    (log/info [base-response response])
    (remember! opts response)))

(defn inspect
  [opts base-response]
  (let [{:keys [query-fn]} opts]
    {:response "heyzz"}
    )

  )

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        context (get-context request)
        request-with-context (assoc params :context context)
        base-response {:sid (get-in request [:session :sid])
                       :request request-with-context
                       :ts (unix-ts)}]
    (cond (str/starts-with? (:msg params) "/inspect")
          (do (log/info "inspect")
              (-> base-response
                  (assoc :response (inspect opts base-response))))
          :else
          (converse! opts base-response))))

(defn converse!
  [_opts request]
  (http-response/ok
   {:todo "FIXME"}))

;;; Scratch
