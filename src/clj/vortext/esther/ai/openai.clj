(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [clojure.pprint :as pprint]
   [clojure.tools.logging :as log]
   [wkok.openai-clojure.api :as api]
   [vortext.esther.config :refer [secrets]]))

(def model "gpt-3.5-turbo")

(defonce api-key (:openai-api-key (secrets)))

(defn prompt
  [history msg]
  (slurp (io/resource "prompts/prompt-gpt3.org")))

(defn parse-result
  [resp]
  (let [r ((comp :content :message first)
           (get-in resp [:choices]))]
    (try
      (json/parse-string r true)
      (catch Exception e
        (log/warn [e resp])
        {:response (str r)}))))

(defn as-role
  [role e]
  {:role role
   :content (json/generate-string e)})

(defn format-for-completion
  [history]
  (let [user (partial as-role "user")
        assistant (partial as-role "assistant")
        coversation-seq (interleave
                         (map user (map :request history))
                         (map assistant (map :response history)))]
    coversation-seq))

(defn chat-completion
  [history msg]
  (let [conv (format-for-completion history)
        submission
        (concat
         [{:role "system" :content (prompt history msg)}]
         conv
         [{:role "user" :content (json/generate-string msg)}])]
    (log/trace "CONVERSATION")
    (log/trace (pprint/pprint conv))
    (log/trace "SUBMISSION")
    (log/trace (pprint/pprint submission))
    (api/create-chat-completion
     {:model model
      :messages submission}
     {:api-key api-key})))

(defn complete
  [history msg]
  (let [completion (chat-completion history msg)]
    (parse-result completion)))

;; Scratch
(comment

  (def history @vortext.esther.web.controllers.converse/*history))
