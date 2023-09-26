(ns vortext.esther.web.controllers.chat
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.util.time :as time]
   [vortext.esther.web.controllers.memory :as memory]))


(defn ->memory-context
  [{:keys [:memory/events :memory/ts]}]
  (let [relevant-ks [:content :emoji :imagination]
        format-event (fn [{:keys [:event/content :event/role]}]
                       (merge
                        {:role role
                         :content (select-keys  content relevant-ks)}
                        (when (= role :user)
                          {:moment (time/human-time-ago
                                    (time/->local-date-time ts))})))]
    (map format-event events)))


(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        memories (reverse (memory/recent-conversation opts user 5))]
    (merge obj {:user/keywords keywords
                :user/memories (mapcat ->memory-context memories)})))


(defn converse!
  [opts user obj]
  (let [obj (->user-context opts user obj)
        complete (get-in opts [:ai :llm :complete-fn])
        response (:llm/response (complete obj))]
    {:event/content (-> response (assoc :ui/type :md-serif))
     :event/role :model
     :event/conversation? true}))
