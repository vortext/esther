(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [vortext.esther.config :refer [response-keys request-keys]]
   [vortext.esther.ai.llama :as llama]))


(defn generate-prompt
  [prompt context]
  (mustache/render prompt context))

(defn generate-submission
  [opts request]
  (let [promt-str (slurp (io/resource (:prompt opts)))
        context (:request-context request)
        prompt  (generate-prompt promt-str context)]
    (concat
     [{:role "system"
       :content prompt}]
     [{:role "user"
       :content (select-keys request request-keys)}])))

(defn create-complete-fn
  [llm-complete]
  (fn [opts user request]
    (let [submission (generate-submission opts request)]
      (select-keys
       ((:complete-fn llm-complete) user submission)
       response-keys))))


(defmethod ig/init-key :ai.llm/llm-interface
           [_ {:keys [impl]
                     :as   opts}]
           (let [instance (case impl :llama-shell (llama/create-interface opts))]
             {:impl instance
                    :shutdown-fn (:shutdown-fn instance)
                    :complete-fn  (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
