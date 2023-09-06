(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [diehard.core :as dh]
   [vortext.esther.config :refer [response-keys request-keys errors]]
   [vortext.esther.util.time :refer [human-today]]
   [vortext.esther.util.markdown :refer [strs-to-markdown-list]]
   [vortext.esther.ai.llama :as llama]))


(defn generate-prompt
  [prompt context]
  (mustache/render
   prompt
   {:today (human-today)
    :context (strs-to-markdown-list context)}))

(defn generate-submission
  [opts context request]
  (let [promt-str (slurp (io/resource (:prompt opts)))
        prompt  (generate-prompt promt-str context)]
    (concat
     [{:role "system"
       :content prompt}]
     [{:role "user"
       :content (select-keys request request-keys)}])))

(defn create-complete-fn
  [llm-complete]
  (fn [opts user context request]
    (dh/with-retry
        {:retry-on Exception
         :max-retries 1
         :on-retry
         (fn [_val ex] (log/warn "llm::complete:retrying..." ex))
         :on-failure
         (fn [_val ex]
           (let [response (:internal-server-error errors)]
             (log/warn "llm::complete:failed..." ex response)
             response))
         :on-failed-attempt
         (fn [_ _] (log/warn "llm::complete:failed-attempt..."))}
      (select-keys
       ((:complete-fn llm-complete) user (generate-submission opts context request))
       response-keys))))


(defmethod ig/init-key :ai.llm/llm-interface
  [_ {:keys [impl]
      :as   opts}]
  (let [instance (case impl :llama-shell (llama/create-complete-shell opts))]
    {:impl instance
     :complete-fn  (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-interface [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
