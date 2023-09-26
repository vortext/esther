(ns vortext.esther.ai.llm
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vortext.esther.util.json :as json]
   [vortext.esther.ai.llama-jna :refer
    [create-context init-llama-sampler generate-string]]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [malli.core :as m]
   [malli.error :as me]
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


(defn memory-line
  [{:keys [model-prefix user-prefix]} {:keys [role content moment]}]
  (let [prefix (case role
                 :model model-prefix
                 :user user-prefix)]
    (str prefix (when moment (str moment ": "))
         (json/write-value-as-string content))))


(defn submission-str
  [{:keys [system-prefix] :as opts} prompt {:keys [:memory/events :user/memories]}]
  (let [request-content (-> events first :event/content :content)
        conversation (str/join "\n" (map (partial memory-line opts) memories))]
    (str/join
     "\n"
     [system-prefix
      prompt
      conversation
      (str (:user-prefix opts)
           "Now: "
           (json/write-value-as-string {:content request-content}))])))


(def steal (atom nil))

(defn ->submission
  [opts obj]
  (log/debug opts)
  (let [template (slurp (io/resource (:prompt opts)))
        ks [:context/today :context/lunar-phase
            :context/allow-location
            :context/weather :context/time-of-day
            :context/season :personality/ai-name]
        context (common/remove-namespaces (select-keys obj ks))
        prompt  (handlebars/render template context)
        submission (submission-str opts prompt obj)]
    (reset! steal obj)
    (log/debug submission)
    (merge
     obj
     {:llm/submission submission})))


(defn extract-json-parse
  [output]
  (when (seq output)
    (let [start (str/index-of output "{")
          end (str/last-index-of output "}")]
      (when (and start end)
        (json/read-json-value (subs output start (inc end)))))))


(defn complete
  [ctx cfg {:keys [:llm/submission]}]
  (let [generated (generate-string ctx submission cfg)]
    (log/debug generated)
    (extract-json-parse generated)))


(defn create-instance
  [{:keys [options]}]
  (let [{:keys [model-path]} options

        ctx (create-context
             (str (fs/canonicalize model-path))
             {:n-ctx (* 4 1024) :n-gpu-layers 23})

        {:keys [grammar-template model-prefix model-suffix end-of-turn]} options
        gbnf-template-path (str (fs/canonicalize (io/resource grammar-template)))
        gbnf-grammar (handlebars/render
                      (slurp gbnf-template-path)
                      {:model-prefix model-prefix
                       :model-suffix model-suffix
                       :end-of-turn end-of-turn})

        sampler (init-llama-sampler ctx gbnf-grammar)]
    {:shutdown-fn
     (fn []
       (.close ctx)
       ((:deletef sampler)))
     :complete-fn
     (fn [obj]
       (let [response (complete
                       ctx
                       {:sampler sampler}
                       (->submission options obj))]
         (assoc obj :llm/response
                (-> response
                    (clean-response)
                    (validate-response)))))}))


(defmethod ig/init-key :ai.llm/llm-interface
  [_ {:keys [impl]
      :as   opts}]
  (let [instance (create-instance opts)]
    {:instance instance
     :shutdown-fn (:shutdown-fn instance)
     :complete-fn (:complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ {:keys [instance]}]
  ((:shutdown-fn instance)))
