(ns vortext.esther.ai.llm
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [vortext.esther.util.json :as json]
   [vortext.esther.ai.llama-jna :as llama]
   [vortext.esther.jna.grammar :refer [init-llama-sampler]]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.common :as common]
   [vortext.esther.util.emoji :as emoji]))


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

(defn context-map
  [obj]
  (common/remove-namespaces
   (select-keys
    obj
    (filter #(#{"context" "personality"} (namespace %)) (keys obj)))))


(defn extract-json-parse
  [output]
  (->
   (json/read-json-value (common/escape-newlines output))
   (update :message common/unescape-newlines)))


(defn complete-submission
  [ctx renderer sampler obj]
  (let [{:keys [:memory/events :user/memories]} obj
        template-vars (merge
                       (context-map obj)
                       {:history memories,
                        :new-message (:event/content (first events))})
        submission ((:handlebars/render-template renderer)
                    "templates/prompt" template-vars)
        _ (log/debug "submission::" submission)
        generated (llama/generate-string
                   ctx submission {:samplef sampler})
        _ (log/debug "generated::" generated)]
    (assoc obj :llm/response
           (-> generated
               (extract-json-parse)
               (validate-response)))))


(defmethod ig/init-key :ai.llm/instance
  [_ {:keys [:llm/params :template/renderer] :as opts}]
  (let [ctx (llama/create-context
             (str (fs/canonicalize (:model-path params))) params)
        gbnf (slurp (str (fs/canonicalize (io/resource (:grammar-file params)))))
        sampler (init-llama-sampler ctx llama/ctx->candidates gbnf params)
        template-vars (:template/vars opts)]

    ((:handlebars/register-helper renderer)
     "prefix" (fn [role _]
                (get-in template-vars [(keyword role) :prefix])))

    ((:handlebars/register-helper renderer)
     "suffix" (fn [role _] (get-in template-vars [(keyword role) :suffix])))

    {:llm/ctx ctx
     :llm/sampler sampler
     :llm/shutdown
     (fn []
       (.close ctx)
       nil)
     :llm/complete (partial complete-submission ctx renderer sampler)}))


(defmethod ig/halt-key! :ai.llm/instance [_ opts]
  ((:llm/shutdown opts)))
