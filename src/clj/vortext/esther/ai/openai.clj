(ns vortext.esther.ai.openai
  (:require
   [vortext.esther.util.mustache :as mustache]
   [vortext.esther.secrets :refer [secrets]]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [vortext.esther.web.controllers.memory :refer [extract-keywords first-image]]
   [vortext.esther.util :refer [parse-maybe-json pretty-json escape-newlines]]
   [jsonista.core :as json]
   [diehard.core :as dh]
   [clojure.set :as set]
   [malli.core :as m]
   [vortext.esther.util.emoji :as emoji]
   [malli.error :as me]
   [vortext.esther.config :refer [examples errors introductions]]
   [wkok.openai-clojure.api :as api]))

(def model "gpt-3.5-turbo")

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.md"))})

(defn relevant-keywords
  [memories keywords]
  (let [memory-keywords (into #{} (map :value keywords))
        without #{"user:new-user" "user:introductions-wanted" "user:returning-user"}
        remainder (set/difference memory-keywords without)
        filter-context #(not (str/starts-with? % "context:"))
        remainder (into #{} (filter filter-context remainder))]
    (if (seq remainder)
      remainder
      (if (seq memories)
        #{"user:returning-user"}
        #{"user:new-user" "user:introductions-wanted"}))))

(defn keywords-to-markdown [keywords]
  (clojure.string/join "\n" (map #(str "- " %) keywords)))

(defn generate-prompt
  [memories keywords]
  (let [example (first (shuffle examples))
        memory-keywords (relevant-keywords memories keywords)
        conversation-keywords (extract-keywords memories)
        relevant-keywords (set/difference
                           memory-keywords
                           conversation-keywords)]
    (log/debug "openai::generate-prompt::relevant-keywords" relevant-keywords)
    (mustache/render
     (:initial scenarios)
     {:keywords (keywords-to-markdown relevant-keywords)
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

(def response-schema
  [:map
   [:response [:and
               [:string {:min 1, :max 2048}]
               [:fn {:error/message "response should be at most 2048 chars"}
                (fn [s] (<= (count s) 2048))]]]
   [:emoji [:fn {:error/message "should contain a valid emoji"}
            (fn [s] (emoji/emoji? s))]]
   [:energy [:fn {:error/message "Energy should be a float between 0 and 1"}
             (fn [e] (and (float? e) (>= e 0.0) (<= e 1.0)))]]])


(defn validate
  [schema obj]
  (if (not (m/validate schema obj))
    (let [error (m/explain schema obj)
          humanized (me/humanize error)]
      (log/warn "openai::validate:error" humanized obj)
      (-> (:validation-error errors)
          (assoc :details humanized)))
    ;; Valid
    obj))

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
                        (get-in completion [:choices 0]))]
      (if-let [json-obj? (parse-maybe-json (escape-newlines first-choice))]
        (validate response-schema json-obj?)
        (:json-parse-error errors)))))


(defn complete
  [_ memories keywords request]
  (let [last-memories (vec (take-last 10 memories))
        prompt (generate-prompt last-memories keywords)

        for-conv (if (seq last-memories)
                   last-memories
                   [(first (shuffle (:imagine introductions)))])
        conv (format-for-completion for-conv)

        last-image (first-image (reverse memories))
        defaults {:keywords (take 3 keywords)
                  :image-prompt last-image}
        submission
        (concat
         [{:role "system"
           :content prompt}]
         conv
         [{:role "user"
           :content (json/write-value-as-string request)}])]
    (merge defaults (openai-api-complete model submission))))



;; Scratch
