(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [response-keys ai-name]]
   [vortext.esther.common :as common]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [clojure.tools.logging :as log]))

(defn ->memory-context
  [{:keys [:memory/events :memory/ts]}]
  (let [relevant-ks [:content :emoji :imagination]]
    {:moment (time/human-time-ago ts)
     :events (map (fn [e]
                    {(:event/role e)
                     (select-keys (:event/content e) relevant-ks)})
                  events)}))

(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        memories (reverse (memory/recent-conversation opts user 5))]
    (merge obj {:user/keywords keywords
                :user/memories (map ->memory-context memories)})))

(defn converse!
  [opts user obj]
  (let [obj (->user-context opts user obj)
        complete (get-in opts [:ai :llm :complete-fn])
        response (:llm/response (complete opts user obj))]
    {:event/content (-> response
                        (assoc :ui/type :md-serif))
     :event/role (keyword ai-name)
     :event/conversation? true}))
