(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [response-keys]]
   [vortext.esther.common :as common]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [clojure.tools.logging :as log]))

(defn ->memory-context
  [{:keys [:memory/events :memory/ts]}]
  (let [relevant-ks [:content :emoji :imagination]
        format-event (fn [{:keys [:event/content :event/role]}]
                       {:role role
                        :content (select-keys  content relevant-ks)})]
    {:moment (time/human-time-ago ts)
     :events (map format-event events)}))

(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        memories (reverse (memory/recent-conversation opts user 5))]
    (merge obj {:user/keywords keywords
                :user/memories (map ->memory-context memories)})))

(defn converse!
  [opts user {:keys [:personality/ai-name] :as obj}]
  (let [obj (->user-context opts user obj)
        complete (get-in opts [:ai :llm :complete-fn])
        response (:llm/response (complete opts user obj))]
    {:event/content (-> response (assoc :ui/type :md-serif))
     :event/role (keyword ai-name)
     :event/conversation? true}))
