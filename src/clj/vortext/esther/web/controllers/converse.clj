(ns vortext.esther.web.controllers.converse
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [malli.core :as m]
    [vortext.esther.common :as common]
    [vortext.esther.errors :refer [wrapped-error]]
    [vortext.esther.util.json :as json]
    [vortext.esther.web.controllers.chat :refer [chat!]]
    [vortext.esther.web.controllers.command :refer [command!]]
    [vortext.esther.web.controllers.context :as context]
    [vortext.esther.web.controllers.memory :as memory]))


(def message-maxlength 1024)


(def request-schema
  [:map
   [:message
    [:and
     [:string {:min 1, :max message-maxlength}]
     [:fn {:error/message (format "message should be at most %s chars" message-maxlength)}
      (fn [s] (<= (count s) message-maxlength))]]]])


(defn append-event
  [obj event]
  (update obj :memory/events #(conj % event)))


(defn- respond!
  [opts user obj]
  (let [reply (if (str/starts-with? (common/request-msg obj) "/")
                (command! opts user obj)
                (chat! opts user obj))]
    (with-meta (append-event obj reply) (meta reply))))


(defn as-obj
  [request]
  (let [{:keys [params]} request
        {:keys [client-context content]} params
        client-context (json/read-json-value client-context)]
    (merge
      {:memory/gid (memory/gid)
       :memory/events [{:event/content {:message (str/trim content)}
                        :event/role :user}]}
      (context/from-client-context client-context))))


(defn answer!
  [opts request]
  (let [user (get-in request [:session :user])
        obj (as-obj request)
        request (-> obj :memory/events first :event/content)]
    (try
      (if-not (m/validate request-schema request)
        (append-event
          obj
          (wrapped-error
            :unrecognized-input
            (str "Unrecognized input: "  request)))
        (let [{:keys [:memory/events] :as obj} (respond! opts user obj)
              [_ response] events]
          (if (= (-> response :event/content :ui/type) :htmx)
            ;; Just return without remembering if a UI element
            obj
            ;; Otherwise remember
            (memory/remember! opts user obj))))
      (catch Exception e
        (append-event
          obj
          (wrapped-error :internal-server-error e))))))


;; Scratch
