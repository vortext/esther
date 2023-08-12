(ns vortext.esther.ai.openai
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [vortext.esther.util :refer [read-json-value parse-maybe-json]]
   [jsonista.core :as json]
   [cheshire.core :as cheshire]
   [clostache.parser :as template]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [wkok.openai-clojure.api :as api]
   [vortext.esther.config :refer [secrets]]))

(def model "gpt-3.5-turbo")

(defonce api-key (:openai-api-key (secrets)))

(def base-prompt (slurp (io/resource "prompts/prompt-gpt3.md")))

(defn get-keywords
  [memories]
  (let [break (fn [s] (if (string? s) (str/split s #",") ""))
        keywords (mapcat (comp break :keywords) memories)]
    (vec (into #{} keywords))))

(defn current-user-context
  [memories]
  (if-not (empty? memories)
    (let [keywords (get-keywords memories)
          image-prompt (last (map :image_prompt memories))]
      (log/trace "openai::generate-prompt:keywords" keywords)
      (log/trace "openai::generate-prompt:image-prompt" image-prompt)
      (str
       "\n# Information about the current user"
       "\n## Keywords about the user:\n" (str/join "," keywords)
       "\n## Current scenery for the user (as image descriptions):\n" image-prompt))
    ""))

(def example-input
  {:context
   {:local-time
    "Sat Aug 12 2023 00:40:32 GMT+0200 (Central European Summer Time)",
    :browser-lang "en-US"},
   :msg "I really like sci-fi too! Star Trek is my favorite :D"})

(def example-output
  {:response
   "Ah, a fellow fan of science fiction! The genre offers limitless possibilities and sparks our imagination. Are there any specific science fiction books, movies, or TV shows that you've enjoyed? I'd love to hear your recommendations and discuss them with you!",
   :emoji "🤓",
   :energy 0.7,
   :image-prompt
   "A futuristic cityscape with towering skyscrapers and flying vehicles, depicting the awe-inspiring world of science fiction.",
   :keywords ["likes:sci-fi" "tv:star-trek"]})

(defn generate-prompt
  [memories _msg]
  (template/render
   base-prompt
   {:current-user-context (current-user-context memories)
    :example-input (cheshire/generate-string example-input {:pretty true})
    :example-output (cheshire/generate-string example-output {:pretty true})}))

(defn parse-result
  [resp]
  (let [r ((comp :content :message first)
           (get-in resp [:choices]))]
    (or (parse-maybe-json r) {:response r})))

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
        _ (log/debug "openai::chat-completion:prompt" prompt)
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
    (log/info "openai::complete:chat-completion" completion)
    (parse-result completion)))

;; Scratch
