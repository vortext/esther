(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.config :refer [errors]]
   [vortext.esther.util.time :as time]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.controllers.command :refer [command!]]
   [vortext.esther.web.controllers.chat :refer [converse!]]
   [vortext.esther.api.weatherapi :as weather]
   [malli.core :as m]
   [vortext.esther.util.json :as json]
   [vortext.esther.util.emoji :as emoji]
   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(def request-schema
  [:map
   [:msg [:and
          [:string {:min 1, :max 1024}]
          [:fn {:error/message "msg should be at most 1024 chars"}
           (fn [s] (<= (count s) 1024))]]]
   [:context [:map {:optional true}]]])


(defn create-context
  [_opts _user request]
  (let [{:keys [params]} request
        ctx (json/read-json-value (get params :context ""))
        ip (get-in ctx [:remote-addr :ip])
        current-weather (weather/current-weather ip)
        more-ctx {:weather current-weather
                  :today (time/human-today)}]
    (merge ctx more-ctx)))

(defn- respond!
  [opts user data]
  (try
    (if (str/starts-with? (get-in data [:request :msg]) "/")
      (command! opts user data)
      (converse! opts user data))
    (catch Exception e
      (do (log/warn e)
          (assoc data :response (:internal-server-error errors))))))

(defn answer!
  [opts request]
  (try
    (let [{:keys [params]} request
          user (get-in request [:session :user])
          data {:request
                {:context (create-context opts user request)
                 :msg (emoji/replace-slack-aliasses (str/trim (:msg params)))}
                :ts (unix-ts)}]
      (if-not (m/validate request-schema (:request data))
        (assoc data :response (:unrecognized-input errors))
        (let [memory (respond! opts user data)
              type (keyword (:type (:response  memory)))]
          (if-not (= type :ui)
            (memory/remember! opts user memory)
            memory))))
    (catch Exception e
      (log/warn e)
      {:request request
       :type :md-serif
       :reply (:internal-server-error errors)})))

;;; Scratch
