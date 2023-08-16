(ns vortext.esther.ai.openai
  (:require
   [vortext.esther.util.mustache :as mustache]
   [vortext.esther.secrets :refer [secrets]]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [vortext.esther.util :refer [parse-maybe-json pretty-json escape-newlines]]
   [jsonista.core :as json]
   [diehard.core :as dh]
   [clojure.set :as set]
   [vortext.esther.config :refer [examples errors introductions]]
   [wkok.openai-clojure.api :as api]))

(def model "gpt-3.5-turbo")
(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.md"))})

(defn extract-keywords
  [memories]
  (into #{} (flatten (map #(get-in % [:response :keywords]) memories))))

(defn relevant-keywords
  [memories keywords]
  (let [conversation-keywords (extract-keywords memories)
        memory-keywords (into #{} (map :value keywords))
        remainder (set/difference conversation-keywords memory-keywords)]
    (if (seq remainder)
      remainder
      (if (seq memories)
        #{"user:returning-user"}
        #{"user:new-user" "user:introductions-need"}))))

(defn keywords-to-markdown [keywords]
  (clojure.string/join "\n" (map #(str "- " %) keywords)))


(defn generate-context
  [memories keywords]
  (let [relevant-keywords (relevant-keywords memories keywords)]
    (keywords-to-markdown relevant-keywords)))

(defn generate-prompt
  [memories keywords]
  (let [example (first (shuffle examples))]
    (mustache/render
     (:initial scenarios)
     {:context (generate-context memories keywords)
      :example-request (pretty-json (:request example))
      :example-response (pretty-json (:response example))})))

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
  (if (seq memories)
    memories
    [(first (shuffle (:imagine introductions)))]))

(defn openai-api-complete
  [model submission]
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
                        (get-in completion [:choices 0]))
          json-obj? (parse-maybe-json (escape-newlines first-choice))]
      (if json-obj? json-obj? (:json-parse-error errors)))))

(defn complete
  [_ memories keywords request]
  (let [prompt (generate-prompt memories keywords)
        conv (format-for-completion (get-contents-memories memories))
        submission
        (concat
         [{:role "system"
           :content prompt}]
         conv
         [{:role "user"
           :content (json/write-value-as-string request)}])]
    (openai-api-complete model submission)))



;; Scratch
