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
   [table.core :as t]
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
                :energy (get response :energy 0)
                :content (json/write-value-as-string answer)
                :keywords (when (seq keywords) (str/join "," keywords))
                :image_prompt (:image-prompt response)}]
    (query-fn :push-memory memory)
    answer))

(defn complete!
  [opts data]
  (let [{:keys [query-fn]} opts
        last-10-memories (reverse (query-fn :last-10-memories {}))
        result (openai/complete last-10-memories (:request data))]
    (log/debug "converse::complete!")
    (remember!
     opts
     (assoc data :response result))))

(defn inspect
  [opts _data]
  (let [{:keys [query-fn]} opts
        result (query-fn :inspect-memory {})]
    (log/debug "converse::inspect")
    {:type :inspect
     :response (t/table-str result  :style :github-markdown)}))

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        context (get-context request)
        request-with-context (assoc params :context context)
        data {:sid (get-in request [:session :sid])
              :request request-with-context
              :ts (unix-ts)}
        command? (str/starts-with? (:msg params) "/")]
    (log/debug "converse::answer!")
    (if command?
      (assoc data :response (inspect opts data))
      (complete! opts data))))


(defn converse!
  [_opts request]
  (http-response/ok
   {:todo "FIXME"}))

;;; Scratch
