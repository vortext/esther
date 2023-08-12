(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [vortext.esther.util :refer [read-json-value parse-maybe-json]]
   [jsonista.core :as json]
   [cheshire.core :as cheshire]
   [clojure.edn :as edn]
   [clostache.parser :as template]
   [clojure.pprint :as pprint]
   [diehard.core :as dh]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [wkok.openai-clojure.api :as api]
   [vortext.esther.config :refer [secrets]]))

(def model "gpt-3.5-turbo")

(defonce api-key (:openai-api-key (secrets)))

(def scenarios
  {:initial (slurp (io/resource "prompts/scenarios/initial.md"))})

(defn current-user-context
  [memories]
  (if-not (empty? memories)
    (let [image-prompt (last (map :image_prompt memories))]
      (log/debug "openai::generate-prompt:image-prompt" image-prompt)
      (str
       "\n # Narrative:"
       "\n## Current scene:\n" image-prompt))
    ""))

(def examples
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/examples.edn"))))

(def errors
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/errors.edn"))))

(def introductions
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/introductions.edn"))))

(defn pretty-json
  [obj]
  (cheshire/generate-string obj {:pretty true}))

(defn generate-prompt
  [memories _msg]
  (let [example (first (shuffle examples))]
    (template/render
     (:initial scenarios)
     {:current-user-context (current-user-context memories)
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
  (let [real (map (comp read-json-value :content) memories)]
    (if (seq real)
      real
      [(first (shuffle (:imagine introductions)))])))

(dh/defratelimiter openai-rl {:rate 12})

(def failed (:internal-server-error errors))

(defn parse-result
  [resp]
  (let [r ((comp :content :message first)
           (get-in resp [:choices]))
        obj? (parse-maybe-json r)]
    (if (associative? obj?)
      obj?
      (:json-parse-error errors))))

(defn openai-api-complete
  [model submission api-key]
  (dh/with-retry
    {:retry-on Exception
     :max-retries 3
     :on-retry
     (fn [_val _ex] (log/warn "openai::openai-api-complete:retrying..."))
     :on-failure
     (fn [_ _]
       (log/warn "openai::openai-api-complete:failed...") failed)
     :on-failed-attempt
     (fn [_ _] (log/warn "openai::openai-api-complete:failed-attempt..."))}
    (dh/with-rate-limiter openai-rl
      (dh/with-timeout {:timeout-ms 12000} ;; 12s
        (parse-result
         (api/create-chat-completion
          {:model model
           :messages submission}
          {:api-key api-key}))))))

(defn chat-completion
  [memories request]
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

    (openai-api-complete model submission api-key)))

(defn complete
  [memories request]
  (let [completion (chat-completion memories request)]
    (log/trace  "openai::complete:chat-completion" completion)
    completion))

;; Scratch
