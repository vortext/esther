(ns vortext.esther.ai.openai
  (:require
   [vortext.esther.secrets :refer [secrets]]
   [clojure.tools.logging :as log]
   [vortext.esther.util :refer
    [parse-maybe-json escape-newlines]]
   [diehard.core :as dh]
   [vortext.esther.config :refer [errors]]
   [wkok.openai-clojure.api :as api]))

(def model "gpt-3.5-turbo")

(defn openai-api-complete
  [submission]
  (dh/with-retry
      {:retry-on Exception
       :max-retries 2
       :on-retry
       (fn [_val ex] (log/warn "openai::openai-api-complete:retrying..." ex))
       :on-failure
       (fn [_val ex]
         (let [response (:internal-server-error errors)]
           (log/warn "openai::openai-api-complete:failed..." ex response)
           response))
       :on-failed-attempt
       (fn [_ _] (log/warn "openai::openai-api-complete:failed-attempt..."))}
    (let [completion (api/create-chat-completion
                      {:model model
                       :messages submission}
                      {:api-key (:openai-api-key (secrets))})
          first-choice ((comp :content :message)
                        (get-in completion [:choices 0]))]
      (if-let [json-obj? (parse-maybe-json (escape-newlines first-choice))]
        json-obj?
        (:json-parse-error errors)))))

(defn create-api-complete [_] openai-api-complete)
