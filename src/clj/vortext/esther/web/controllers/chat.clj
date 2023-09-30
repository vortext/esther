(ns vortext.esther.web.controllers.chat
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.controllers.memory :as memory]))




(defn ->memory-context
  [{:keys [:memory/events]}]
  (let [format-event (fn [{:keys [:event/content :event/role]}]
                       {:role (name role)
                        :content (dissoc content :ui/type)})]
    (map format-event events)))


(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        ;; TODO calculate tokens so we don't overflow the context ...
        memories (reverse (memory/recent-conversation opts user 10))]
    (merge obj {:user/keywords keywords
                :user/memories (mapcat ->memory-context memories)})))


(defn chat!
  [opts user obj]
  (let [obj (->user-context opts user obj)
        complete (get-in opts [:ai/llm :llm/complete])
        response (:llm/response (complete obj))]
    {:event/content (-> response (assoc :ui/type :md-serif))
     :event/role :model
     :event/conversation? true}))
