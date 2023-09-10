(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.config :refer [response-keys request-keys]]
   [vortext.esther.common :as common]
   [vortext.esther.ai.llama :as llama]
   [malli.core :as m]
   [malli.error :as me]))


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
      (throw (Exception. humanized)))
    ;; Valid
    obj))


(def validate-response (partial validate response-schema))

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

(defn generate-prompt
  [prompt context]
  (mustache/render prompt context))

(defn ->submission
  [opts obj]
  (let [promt-str (slurp (io/resource (:prompt opts)))
        {:keys [:local/context :memory/events :user/memories :user/keywords]} obj
        prompt  (generate-prompt promt-str context)
        request-content (-> events first :event/content :content)]
    {:llm/prompt prompt
     :llm/submission
     {:context {:memories memories
                :keywords keywords}
      :content request-content}}))


(defn create-complete-fn
  [llm-complete]
  (fn [opts user obj]
    (let [obj (->submission opts obj)
          response ((:complete-fn llm-complete) user obj)]
      (assoc
       obj :llm/response
       (-> response
           (clean-response)
           (validate-response))))))


(defmethod ig/init-key :ai.llm/llm-interface
  [_ {:keys [impl]
      :as   opts}]
  (let [instance (case impl :llama-shell (llama/create-interface opts))]
    {:impl instance
     :shutdown-fn (:shutdown-fn instance)
     :complete-fn  (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
