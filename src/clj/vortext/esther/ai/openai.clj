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

   [malli.error :as me]
   [vortext.esther.config :refer [examples errors introductions]]
   [wkok.openai-clojure.api :as api])
  (:import [com.vdurmont.emoji EmojiManager]))

(def model "gpt-3.5-turbo")

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.md"))})

(defn relevant-keywords
  [memories keywords]
  (let [memory-keywords (into #{} (map :value keywords))
        without #{"user:new-user" "user:introductions-need" "user:returning-user"}
        remainder (set/difference memory-keywords without)
        to-include (if (seq remainder)
                     remainder
                     (if (seq memories)
                       #{"user:returning-user"}
                       #{"user:new-user" "user:introductions-need"}))]
    to-include))

(defn keywords-to-markdown [keywords]
  (clojure.string/join "\n" (map #(str "- " %) keywords)))

(defn generate-prompt
  [memories keywords]
  (let [example (first (shuffle examples))
        memory-keywords (relevant-keywords memories keywords)
        conversation-keywords (extract-keywords memories)
        relevant-keywords (set/intersection
                           memory-keywords
                           conversation-keywords)
        newest-first (reverse memories)
        last-image (first-image newest-first)
        has-image? (:image-prompt (first newest-first))]
    (log/debug "openai::generate-prompt::relevant-keywords" relevant-keywords)
    (log/debug "openai::generate-prompt::last-image" last-image)

    (mustache/render
     (:initial scenarios)
     {:keywords (keywords-to-markdown relevant-keywords)
      :last-image last-image
      :has-keywords? (boolean (seq relevant-keywords))
      :has-image? (boolean (not has-image?))
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


(def response-schema
  [:map
   [:response [:and
               [:string {:min 1, :max 2048}]
               [:fn {:error/message "response should be at most 2048 chars"}
                (fn [s] (<= (count s) 2048))]]]
   [:emoji [:fn {:error/message "should be a valid emoji"}
            (fn [s] (EmojiManager/containsEmoji ^String s))]] ;; Using emoji-java
   [:energy [:or [:string {:min 0, :max 10}]
             [:fn {:error/message "Energy should be a float between 0 and 1"}
              (fn [e] (and (float? e) (>= e 0.0) (<= e 1.0)))]]]

   [:keywords [:vector {:optional true} :string]]
   [:image-prompt [:string {:optional true, :min 1}]]])


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
  (let [prompt (generate-prompt memories keywords)
        ;;_ (log/debug "openai::complete:prompt" prompt)
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
