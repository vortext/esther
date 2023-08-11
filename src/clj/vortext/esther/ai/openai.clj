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

(defn generate-prompt
  [memories _msg]
  (let [scenario (cond
                   (seq memories) :continue
                   (empty? memories) :initial
                   :else :initiate)]
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
          {:response (str maybe-json)})))))

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
  [memories]
  (let [user (partial as-role "user")
        assistant (partial as-role "assistant")
        coversation-seq (interleave
                         (map user (map :request memories))
                         (map assistant (map :response memories)))]
    coversation-seq))

(defn chat-completion
  [memories msg]
  (let [conv (format-for-completion memories)
        submission
        (concat
         [{:role "system"
           :content (generate-prompt memories msg)}]
         conv
         [{:role "user"
           :content (json/write-value-as-string msg)}])]
    (log/trace "CONVERSATION")
    (log/trace (pprint/pprint conv))
    (log/trace "SUBMISSION")
    (log/trace  (pprint/pprint submission))
    (api/create-chat-completion
     {:model model
      :messages submission}
     {:api-key api-key})))

(defn complete
  [memories msg]
  (let [completion (chat-completion memories msg)]
    (parse-result completion)))

;; Scratch
