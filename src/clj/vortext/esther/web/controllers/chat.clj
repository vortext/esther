(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [response-keys ai-name]]
   [vortext.esther.errors :refer [errors wrapped-error]]
   [vortext.esther.common :as common]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [vortext.esther.util.emoji :as emoji]
   [malli.core :as m]
   [malli.error :as me]
   [clojure.tools.logging :as log]))

(def response-schema
  [:map
   [:content
    [:and
     [:string {:min 1, :max 2048}]
     [:fn {:error/message "response should be at most 2048 chars"}
      (fn [s] (<= (count s) 2048))]]]
   [:emoji [:fn {:error/message "should contain a valid emoji"}
            (fn [s] (emoji/unicode-emoji? s))]]
   [:energy [:fn {:error/message "Energy should be a float between 0 and 1"}
             (fn [e] (and (float? e) (>= e 0.0) (<= e 1.0)))]]])

(defn validate
  [schema obj]
  (if (not (m/validate schema obj))
    (let [error (m/explain schema obj)
          humanized (me/humanize error)]
      (log/warn "llm::validate:error" obj humanized)
      (-> (:validation-error errors)
          (assoc :details humanized)))
    ;; Valid
    obj))

(def clean-energy
  (partial
   common/update-value :energy
   #(let [parsed-val
          (or (when (and (float? %) (<= 0 % 1)) %)
              (common/parse-number (str %)))]
      (when (and parsed-val (<= 0 parsed-val 1))
        (min 0.999 (float parsed-val))))))

(def clean-emoji
  (partial
   common/update-value :emoji
   emoji/extract-first-emoji))

(defn clean-response
  [response]
  (-> response
      (clean-energy 0.5)
      (clean-emoji "🙃")))

(defn ->memory-context
  [{:keys [:memory/events :memory/ts]}]
  (let [relevant-ks [:content :emoji :imagination]]
    {:moment (time/human-time-ago ts)
     :events (map #(merge
                    {:role (:event/role %)}
                    (select-keys (:event/content %) relevant-ks))
                  events)}))

(defn ->user-context
  [opts user obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))
        memories (memory/recent-conversation opts user)]
    (merge obj {:user/keywords keywords
                :user/memories (map ->memory-context memories)})))

(defn ->event
  [content]
  {:event/conversation? true
   :event/role (keyword ai-name)
   :event/content (assoc content :ui/type :md-serif)})

(defn converse!
  [opts user obj]
  (let [llm-complete (get-in opts [:ai :llm :complete-fn])
        validate-response #(validate response-schema %)
        response
        (-> (llm-complete opts user (->user-context opts user obj))
            (clean-response)
            (validate-response))]
    (->event response)))
