(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [vortext.esther.util :refer [read-json-value]]
   [jsonista.core :as json]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.data.csv :as csv]
   [wkok.openai-clojure.api :as api]
   [vortext.esther.config :refer [secrets]]))

(def model "gpt-3.5-turbo")

(defonce api-key (:openai-api-key (secrets)))

(def prompt (slurp (io/resource "prompts/prompt-gpt3.org")))

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.org"))
   :continue (slurp (io/resource "prompts/scenarios/continue.org"))
   :initiate (slurp (io/resource "prompts/scenarios/initiate.org"))})

(defn get-keywords
  [memories]
  (let [csv (str/join "," (map :keywords memories))
        parts (str/split csv #",")
        kw (vec (into #{} parts))]
    kw))

(defn example-org-prompt
  [memory]
  (let [mapper (json/object-mapper {:pretty true})]
    (str/join
     "\n"
     ["* Example from the conversation"
      "** Input"
      (json/write-value-as-string (:request memory) mapper)
      "** Output"
      (json/write-value-as-string (:response memory) mapper)])))


(defn generate-prompt
  [memories _msg]
  (let [scenario :initial
        keywords (get-keywords memories)]
    (log/info "openai::generate-prompt:keywords" keywords)
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
      {:response (str maybe-json)})))

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



(defn get-contents-memories
  [memories]
  (map (comp read-json-value :content) memories))

(defn chat-completion
  [memories msg]
  (let [
        prompt (generate-prompt memories msg)
        conv (format-for-completion
              (get-contents-memories memories))
        submission
        (concat
         [{:role "system"
           :content prompt}]
         conv
         [{:role "user"
           :content (json/write-value-as-string msg)}])]
    (api/create-chat-completion
     {:model model
      :messages submission}
     {:api-key api-key})))

(defn complete
  [memories msg]
  (let [completion (chat-completion memories msg)]
    (parse-result completion)))

;; Scratch
