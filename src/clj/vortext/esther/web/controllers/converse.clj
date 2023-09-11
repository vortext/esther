(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts] :as time]
   [vortext.esther.errors :refer [errors wrapped-error]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.controllers.command :refer [command!]]
   [vortext.esther.web.controllers.chat :refer [converse!]]
   [vortext.esther.util :refer [random-base64]]
   [vortext.esther.common :refer [request-msg]]
   [vortext.esther.api.weatherapi :as weather]
   [malli.core :as m]
   [vortext.esther.util.json :as json]
   [vortext.esther.config :as config]
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

(defn append-event
  [obj event]
  (update obj :memory/events #(conj % event)))

(defn- respond!
  [opts user obj]
  (try
    (let [reply (if (str/starts-with? (request-msg obj) "/")
                  (command! opts user obj)
                  (converse! opts user obj))]
      (append-event obj reply))
    (catch Exception e
      (append-event obj (wrapped-error :internal-server-error e)))))

(defn create-local-context
  [context]
  (let [extra {:today (time/human-today
                       (or (:timezone context)
                           time/default-zone-id) time/default-locale)
               :name (str/capitalize config/ai-name)}
        context (merge context extra)
        {:keys [latitude longitude]} (:location context)
        weather-q (str latitude "," longitude)]
    (if (:location-allowed context)
      (merge context {:weather (weather/current-weather weather-q)})
      context)))

(defn make-request-obj
  [user request]
  (let [{:keys [params]} request
        {:keys [context content]} params
        local-context (create-local-context (json/read-json-value context))]
    {:local/context local-context
     :memory/ts (unix-ts)
     :memory/gid (random-base64)
     :memory/events [{:event/content {:content content}
                      :event/role :user}]}))

(defn answer!
  [opts request]
  (let [user (get-in request [:session :user])
        obj (make-request-obj user request)
        _ (log/debug "answer!obj:" obj)
        request (-> obj :memory/events first :event/content)]
    (try
      (if-not (m/validate request-schema request)
        (append-event
         obj
         (wrapped-error :unrecognized-input (str "Unrecognized input: "  request)))
        (let [new-obj (respond! opts user obj)
              [_ resp] (:memory/events new-obj)
              {:keys [:ui/type]} (:event/content resp)]
          (if-not (= type :ui)
            (memory/remember! opts user new-obj)
            ;; Just return without remembering if UI
            new-obj)))
      (catch Exception e
        (append-event
         obj
         (wrapped-error :internal-server-error e))))))

;;; Scratch
