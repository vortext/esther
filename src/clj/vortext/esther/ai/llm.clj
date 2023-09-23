(ns vortext.esther.ai.llm
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.ai.llama :as llama]
   [vortext.esther.common :as common]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.util.handlebars :as handlebars]))


(def response-schema
  [:map
   [:content
    [:and
     [:string {:min 1, :max 2048}]
     [:fn {:error/message "response should be at most 2048 chars"}
      (fn [s] (<= (count s) 2048))]]]
   [:emoji [:fn {:error/message "should contain a valid emoji"}
            (fn [s] (emoji/unicode-emoji? s))]]
   [:imagination [:string {:min 1, :max 2048}]]])


(defn validate
  [schema obj]
  (if (not (m/validate schema obj))
    (let [error (m/explain schema obj)
          humanized (me/humanize error)]
      (throw (Exception. (str humanized))))
    ;; Valid
    obj))


(def validate-response (partial validate response-schema))


(def clean-emoji
  (partial
   common/update-value :emoji
   emoji/extract-first-emoji))


(defn clean-response
  [response]
  (-> response (clean-emoji "🙃")))


(defn ->submission
  [opts {:keys [:memory/events :user/memories :user/keywords] :as obj}]
  (let [template (slurp (io/resource (:prompt opts)))
        ks [:context/today :context/lunar-phase
            :context/allow-location
            :context/weather :context/time-of-day
            :context/season :personality/ai-name]
        context (common/remove-namespaces (select-keys obj ks))
        prompt  (handlebars/render template context)
        request-content (-> events first :event/content :content)]
    (merge
     obj
     {:llm/prompt-template template
      :llm/prompt prompt
      :llm/submission
      {:context {:memories memories
                 :keywords keywords}
       :content request-content}})))


(defn create-complete-fn
  [instance]
  (fn [opts user obj]
    (let [obj (->submission opts obj)
          response ((:complete-fn instance) user obj)
          response (-> response
                       (clean-response)
                       (validate-response))]
      (assoc obj :llm/response response))))


(defmethod ig/init-key :ai.llm/llm-interface
  [_ {:keys [impl]
      :as   opts}]
  (let [instance (case impl :llama-shell (llama/create-instance opts))]
    {:instance instance
     :shutdown-fn (:shutdown-fn instance)
     :complete-fn (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ {:keys [instance]}]
  ((:shutdown-fn instance)))
