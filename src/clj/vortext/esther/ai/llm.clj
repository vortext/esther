(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [diehard.core :as dh]
   [vortext.esther.ai.llama :as llama]
   [vortext.esther.config :refer [errors]]))


(defn generate-prompt
  [prompt]
  (mustache/render prompt {}))

(def response-keys
  #{:response :keywords
    :emoji :energy :image-prompt})

(def request-keys
  #{:msg :keywords})

(defn generate-submission
  [opts request]
  (let [prompt (generate-prompt (slurp (io/resource (:prompt opts))))]
    (concat
     [{:role "system"
       :content prompt}]
     [{:role "user"
       :content (select-keys request request-keys)}])))

(defn create-complete-fn
  [llm-complete]
  (fn [opts user request]
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
       ((:complete-fn llm-complete) user (generate-submission opts request))
       response-keys))))


(defmethod ig/init-key :ai.llm/llm-complete
  [_ {:keys [impl]
      :as   opts}]
  (let [instance
        (case impl
          :llama-shell (llama/create-complete-shell opts))]
    {:impl instance
     :complete-fn  (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-complete [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
