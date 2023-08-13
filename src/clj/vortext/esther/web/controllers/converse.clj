(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [camel-snake-kebab.core :as csk]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util.security :refer [random-base64]]
   [vortext.esther.util :refer [read-json-value]]
   [vortext.esther.config :refer [errors]]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
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
        {:keys [connection query-fn]} opts
        uid "<user>"
        response (:response answer)
        memory-gid (random-base64)
        keywords (map (fn [kw] (csk/->kebab-case (str/trim kw)))
                      (get response :keywords []))
        _ (log/debug "converse::remember!" keywords)
        memory {:gid memory-gid
                :sid (:sid answer)
                :uid uid
                :content (json/write-value-as-string answer)}]
    (jdbc/with-transaction [tx connection]
      (query-fn tx :push-memory memory)
      (doall
       (map (fn [kw] (query-fn tx :see-keyword {:uid uid :keyword kw})) keywords)) )
    answer))

(defn contents-as-memories
  [jsons]
  (map (comp read-json-value :content) jsons))

(defn complete!
  [uid opts data]
  (let [{:keys [query-fn]} opts
        last-10-memories (contents-as-memories
                          (query-fn :last-n-memories {:uid uid :n 10}))
        last-10-memories (reverse last-10-memories)
        result (openai/complete opts last-10-memories (:request data))]
    (log/debug "converse::complete!")
    (remember!
     opts
     (assoc data :response result))))


(defn inspect
  [uid opts _data]
  (let [{:keys [query-fn]} opts
        memories (contents-as-memories
                  (query-fn :last-n-memories {:uid uid :n 5}))
        responses (map (fn [memory]
                         (let [response (:response memory)]
                           (assoc response :keywords (clojure.string/join ", " (:keywords response)))))
                       memories)
        ks [:emoji :energy :keywords :image-prompt]]
    (log/debug "converse::inspect")
    {:type :inspect
     :response
     (t/table-str
      (map #(select-keys % ks) responses)
      :style :github-markdown)}))




(defn answer!
  [opts request]
  (let [{:keys [params]} request
        uid "<user>"

        context (get-context request)
        request-with-context (assoc params :context context)
        data {:sid (get-in request [:session :sid])
              :request request-with-context
              :ts (unix-ts)}
        command? (str/starts-with? (:msg params) "/")]
    (log/debug "converse::answer!")
    (try
      (if command?
        (assoc data :response (inspect uid opts data))
        (complete! uid opts data))
      (catch Exception _e
        (assoc data :response (:internal-server-error errors))))))


(defn converse!
  [_opts request]
  (http-response/ok
   {:todo "FIXME"}))

;;; Scratch
