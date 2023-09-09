(ns vortext.esther.web.controllers.chat
  (:require
   [vortext.esther.config :refer [errors response-keys wrapped-error]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.util.time :as time]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.common :as common]
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

(defn ->user-context
  [opts user obj]

  (let [keywords (memory/frecency-keywords opts user :week 10)
        keywords (into #{} (map :value keywords))

        k 3 ;; [FIXME] simply put the conversation? flag in the database
        memories (filter :memory/conversation? (memory/last-memories opts user))
        memories
        (reverse
         (map (fn [{:keys [:converse/request :converse/response :local/ts]}]
                {:moment (time/human-time-ago ts)
                 :request (select-keys request [:content])
                 :response (select-keys response [:emoji :content :imagination])})
              (take k memories)))]
    (merge obj {:user/keywords keywords
                :user/memories memories})))


(defn converse!
  [opts user obj]
  (let [llm-complete (get-in opts [:ai :llm :complete-fn])
        validate-response #(validate response-schema %)]
    (try
      (-> obj
          (merge {:memory/conversation? true
                  :ui/type :md-serif
                  :converse/response
                  (-> (llm-complete opts user (->user-context opts user obj))
                      (clean-response)
                      (validate-response))}))
      (catch Exception e (wrapped-error :internal-server-error e)))))
