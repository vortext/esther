(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [errors response-keys]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.common :as common ]
   [clojure.tools.logging :as log]))

(def response-schema
  [:map
   [:reply
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

(defn converse!
  [opts user data]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (if-let [kws (seq keywords)]
                   (into #{} (map :value kws))
                   #{"user:new-user"})

        k 3
        conversation-memories (filter (comp :conversation? :response)
                                      (memory/last-memories opts user 10))
        memories (reverse
                  (map (fn [{:keys [request response ts]}]
                         {:moment (time/human-time-ago ts)
                          :request (select-keys request [:msg])
                          :response (select-keys response response-keys)
                          }) (take k conversation-memories)))
        request-context (get-in data [:request :context])
        request (-> (:request data)
                    (assoc :request request-context)
                    (assoc :context {:memories memories
                                     :keywords keywords}))
        llm-complete (get-in opts [:ai :llm :complete-fn])
        ;; The actual LLM complete
        response (llm-complete opts user request)
        validate-response #(validate response-schema %)]
    (try
      (-> data
          (assoc
           :response
           (-> response
               (clean-response)
               (validate-response)
               (assoc :conversation? true)
               (assoc :type :md-serif))))
      (catch Exception e (do (log/warn e) (:internal-server-error errors))))))
