(ns vortext.esther.ai.llm
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [vortext.esther.util.json :as json]
   [vortext.esther.ai.llama-jna :refer
    [create-context init-llama-sampler generate-string *num-threads*]]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.common :as common]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.util.handlebars :as handlebars]))


(def response-schema
  [:map
   [:message
    [:string {:min 1, :max 2048}]]
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

(defn memory-line
  [{:keys [model-prefix user-prefix]} {:keys [role content]}]
  (let [prefix (case role
                 :model model-prefix
                 :user user-prefix)]
    (str prefix (json/write-value-as-string content))))


(defn create-submission
  [{:keys [system-prefix user-prefix] :as opts}
   {:keys [:memory/events :user/memories] :as obj}]
  (let [template (slurp (io/resource (:prompt opts)))
        ks [:context/today :context/lunar-phase
            :context/allow-location
            :context/weather :context/time-of-day
            :context/season :personality/ai-name]
        context (common/remove-namespaces (select-keys obj ks))
        prompt  (handlebars/render template context)
        request-content (-> events first :event/content)
        conversation (str/join "\n" (map (partial memory-line opts) memories))]
    (str/join
     [(str system-prefix prompt)
      (str conversation "\n")
      (str user-prefix (json/write-value-as-string request-content))])))


(defn extract-json-parse
  [output]
  (when (seq output)
    (let [start (str/index-of output "{")
          end (str/last-index-of output "}")]
      (when (and start end)
        (json/read-json-value (subs output start (inc end)))))))




(defmethod ig/init-key :ai.llm/llm-interface
  [_ {:keys [options]}]
  (let [{:keys [model-path]} options
        _       (log/debug options)
        ctx (binding [*num-threads* 32]
              (create-context
               (str (fs/canonicalize model-path))
               {:n-ctx (* 4 1024) :n-gpu-layers 26}))

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
       ((:deletef sampler))
       nil)
     :complete-fn
     (fn [obj]
       (let [submission (create-submission options obj)
             _ (log/debug "submission::" submission)
             generated (generate-string
                        ctx submission {:sampler sampler})
             _ (log/debug "generated::" generated)]
         (assoc obj :llm/response
                (-> generated
                    (extract-json-parse)
                    (validate-response)))))}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ opts]
  ((:shutdown-fn opts)))
