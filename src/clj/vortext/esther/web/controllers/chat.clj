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
  (let [n-keywords 10
        n-memories 10
        keywords (memory/frecency-keywords opts user :week n-keywords)
        keywords (into #{} (map :value keywords))
        ;; TODO calculate n-memories based on context size
        memories (reverse (memory/recent-conversation opts user n-memories))]
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
