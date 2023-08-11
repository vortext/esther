(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [jsonista.core :as json]
   [cheshire.core :as cheshire]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [wkok.openai-clojure.api :as api]
   [vortext.esther.config :refer [secrets]]))

(def model "gpt-3.5-turbo")

(defonce api-key (:openai-api-key (secrets)))

(def prompt (slurp (io/resource "prompts/prompt-gpt3.org")))

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.org"))
   :continue (slurp (io/resource "prompts/scenarios/continue.org"))
   :initiate (slurp (io/resource "prompts/scenarios/initiate.org"))})

(defn prompt
  [history msg]
  (let [scenario (cond
                   (seq history) :continue
                   (empty? history) :initial
                   :else :initiate)]
    (log/info "scenario" scenario)
    (str/join
     "\n"
     [prompt
      (scenario scenarios)])))

(defn parse-maybe-json
  [maybe-json]
  (try
    (json/read-value
     maybe-json
     json/keyword-keys-object-mapper)
    (catch Exception _
      (try ;; Try cheshire?
        (cheshire/decode maybe-json true)
        (catch Exception _
          (str maybe-json))))))

(defn parse-result
  [resp]
  (let [r ((comp :content :message first)
           (get-in resp [:choices]))]
    (try
      (parse-maybe-json r)
      (catch Exception e
        (log/warn [e resp])
        {:response (str r)}))))

(defn as-role
  [role e]
  {:role role
   :content (json/write-value-as-string e)})

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
         [{:role "system"
           :content (prompt history msg)}]
         conv
         [{:role "user"
           :content (json/write-value-as-string msg)}])]
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
