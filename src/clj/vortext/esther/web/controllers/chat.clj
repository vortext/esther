(ns vortext.esther.web.controllers.chat
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.util.time :as time]
   [vortext.esther.web.controllers.memory :as memory]))


(defn as-moment [ts]
  (time/human-time-ago
   (time/->local-date-time ts)))

(defn ->memory-context
  [{:keys [:memory/events :memory/ts]}]
  (let [relevant-ks [:message :emoji :keywords :imagination]
        format-event (fn [{:keys [:event/content :event/role]}]
                       {:role role
                        :content
                        (merge (select-keys content relevant-ks)
                               (when (= role :user)
                                 {:moment (as-moment ts)}))})]
    (map format-event events)))


(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        ;; TODO calculate tokens so we don't overflow the context ...
        memories (reverse (memory/recent-conversation opts user 5))]
    (merge obj {:user/keywords keywords
                :user/memories (mapcat ->memory-context memories)})))


(defn chat!
  [opts user obj]
  (let [obj (->user-context opts user obj)
        complete (get-in opts [:ai :llm :complete-fn])
        response (:llm/response (complete obj))]
    {:event/content (-> response (assoc :ui/type :md-serif))
     :event/role :model
     :event/conversation? true}))
