(ns vortext.esther.web.controllers.converse
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [malli.core :as m]
   [vortext.esther.common :as common]
   [vortext.esther.errors :refer [wrapped-error]]
   [vortext.esther.util.json :as json]
   [vortext.esther.web.controllers.chat :refer [converse!]]
   [vortext.esther.web.controllers.command :refer [command!]]
   [vortext.esther.web.controllers.context :as context]
   [vortext.esther.web.controllers.memory :as memory]))


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
  (let [reply (if (str/starts-with? (common/request-msg obj) "/")
                (command! opts user obj)
                (converse! opts user obj))]
    (append-event obj reply)))


(defn as-obj
  [opts request]
  (let [{:keys [params]} request
        {:keys [client-context content]} params
        client-context (json/read-json-value client-context)]
    (merge
     {:personality/ai-name (get-in opts [:ai :name])
      :memory/gid (memory/gid)
      :memory/events [{:event/content {:content content}
                       :event/role :user}]}
     (context/from-client-context client-context))))


(defn answer!
  [opts request]
  (let [user (get-in request [:session :user])
        obj (as-obj opts request)
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
          (if-not (= (:ui/type response) :htmx)
            (memory/remember! opts user obj)
            ;; Just return without remembering if not conversation?
            obj)))
      (catch Exception e
        (append-event
         obj
         (wrapped-error :internal-server-error e))))))

;; Scratch
