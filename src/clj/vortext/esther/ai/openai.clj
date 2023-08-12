(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [vortext.esther.util :refer [read-json-value parse-maybe-json]]
   [jsonista.core :as json]
   [cheshire.core :as cheshire]
   [clostache.parser :as template]
   [clojure.pprint :as pprint]
   [diehard.core :as dh]
   [vortext.esther.config :refer [secrets examples errors introductions]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [wkok.openai-clojure.api :as api]))

(def model "gpt-3.5-turbo")

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.md"))})


(defn pretty-json
  [obj]
  (cheshire/generate-string obj {:pretty true}))

(defn generate-prompt
  [_memories _msg]
  (let [example (first (shuffle examples))]
    (template/render
     (:initial scenarios)
     {:example-request (pretty-json (:request example))
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
  (let [real (map (comp read-json-value :content) memories)]
    (if (seq real)
      real
      [(first (shuffle (:imagine introductions)))])))

(defonce api-key (:openai-api-key (secrets)))

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
                      {:api-key api-key})
          first-choice ((comp :content :message first)
                        (get-in completion [:choices]))
          json-obj? (parse-maybe-json first-choice)]
      (if json-obj? json-obj? (:json-parse-error errors)))))

(defn complete
  [opts memories request]
  (let [prompt (generate-prompt memories request)
        _ (log/trace "openai::chat-completion:prompt" prompt)
        _ (log/trace "openai::chat-completion:request" request)
        conv (format-for-completion (get-contents-memories memories))
        submission
        (concat
         [{:role "system"
           :content prompt}]
         conv
         [{:role "user"
           :content (json/write-value-as-string request)}])]
    _ (log/trace "openai::chat-completion:submission" submission)
    (openai-api-complete model submission)))



;; Scratch
