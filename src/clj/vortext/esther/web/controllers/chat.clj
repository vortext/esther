(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [errors response-keys]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.common :as common ]
   [clojure.tools.logging :as log]
   [clojure.set :as set]))

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

(defn user-keywords
  [keywords]
  (let [freceny-keywords (into #{} (map :value keywords))
        without #{"user:new-user" "user:returning-user"}
        keywords (set/difference freceny-keywords without)
        default (if (seq keywords) #{"user:returning-user"} #{"user:new-user"})
        result (set/union keywords default)]
    result))

(defn converse!
  [opts user data]
  (let [keywords (memory/frecency-keywords opts user :week 10)
        history (filter (comp :conversation? :response)
                        (memory/last-memories opts user 10))
        history (reverse
                 (map (fn [{:keys [request response ts]}]
                        {:response (select-keys response response-keys)
                         :request (select-keys request [:msg])
                         :moment (time/human-time-ago ts)})
                      (take 3 history)))
        user-keywords (user-keywords keywords)
        current-context (get-in data [:request :context])
        context-keywords (common/namespace-keywordize-map current-context)
        request (-> (:request data)
                    (assoc :context {:history history
                                     :keywords user-keywords}))
        complete (get-in opts [:ai :complete-fn :complete-fn])
        ;; The actual LLM complete
        response (complete opts user context-keywords request)
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
