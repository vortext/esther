(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts] :as time]
   [vortext.esther.config :refer [errors wrapped-error]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.controllers.command :refer [command!]]
   [vortext.esther.web.controllers.chat :refer [converse!]]
   [vortext.esther.util :refer [random-base64]]
   [vortext.esther.api.weatherapi :as weather]
   [malli.core :as m]
   [vortext.esther.util.json :as json]
   [vortext.esther.util.emoji :as emoji]
   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(def request-schema
  [:map
   [:content
    [:and
     [:string {:min 1, :max 1024}]
     [:fn {:error/message "content should be at most 1024 chars"}
      (fn [s] (<= (count s) 1024))]]]])

(defn- respond!
  [opts user {:keys [:converse/request] :as obj}]
  (merge
   obj
   (try
     (if (str/starts-with? (:content request) "/")
       (command! opts user obj)
       (converse! opts user obj))
     (catch Exception e
       (wrapped-error :internal-server-error e)))))

(defn create-local-context
  [context]
  (let [ip (get-in context [:remote-addr :ip])]
    (merge (dissoc context :remote-addr)
           {:weather (weather/current-weather ip)
            :today (time/human-today)})))

(defn make-request-obj
  [request]
  (let [{:keys [params]} request
        {:keys [context content]} params
        context (create-local-context (json/read-json-value context))
        request-content (emoji/replace-slack-aliasses (str/trim content))]
    {:local/gid (random-base64)
     :local/ts (unix-ts)
     :local/context context
     :converse/request {:content request-content}}))

(defn answer!
  [opts request]
  (let [obj (make-request-obj request)
        user (get-in request [:session :user])]
    (if-not (m/validate request-schema (:converse/request obj))
      (wrapped-error :unrecognized-input nil)
      (try
        (let [{:keys [:ui/type] :as response} (respond! opts user obj)]
          (if-not (= type :ui)
            (memory/remember! opts user response)
            ;; Just return
            response))
        (catch Exception e (wrapped-error :internal-server-error e))))))

;;; Scratch
