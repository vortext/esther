(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [vortext.esther.web.controllers.memory :refer
    [extract-keywords]]
   [vortext.esther.util :refer [strs-to-markdown-list]]
   [jsonista.core :as json]
   [clojure.set :as set]
   [malli.core :as m]
   [vortext.esther.util.emoji :as emoji]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.ai.llama :as llama]
   [malli.error :as me]
   [vortext.esther.config :refer [examples errors introductions]]))

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


(defn generate-prompt
  [prompt memories keywords]
  (let [example (first (shuffle examples))
        memory-keywords (relevant-keywords memories keywords)
        conversation-keywords (extract-keywords memories)
        relevant-keywords (set/difference
                           memory-keywords
                           conversation-keywords)]
    (log/debug "llm::generate-prompt::relevant-keywords" relevant-keywords)
    (mustache/render
     prompt
     {:keywords (strs-to-markdown-list relevant-keywords)
      :include-keywords? (boolean (seq relevant-keywords))
      :example-request (json/write-value-as-string (:request example))
      :example-response (json/write-value-as-string (:response example))})))

(defn as-role
  [role e]
  {:role role
   :content (json/write-value-as-string e)})

(def response-keys
  #{:response :keywords
    :emoji :energy :image-prompt})

(def request-keys
  #{:msg :context})

(defn conversation-seq
  [user assistant memories]
  (interleave
   (map user
        (map #(select-keys % request-keys)
             (map :request memories)))
   (map assistant
        (map
         #(select-keys % response-keys)
         (map :response memories)))))

(defn format-for-completion
  [memories]
  (let [user (partial as-role "user")
        assistant (partial as-role "assistant")]
    (conversation-seq user assistant memories)))

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
      (log/warn "llm::validate:error" humanized)
      (-> (:validation-error errors)
          (assoc :details humanized)))
    ;; Valid
    obj))

(def get-prompt
  (memoize
   (fn [path] (slurp (io/resource path)))))

(defn generate-submission
  [opts request memories keywords]
  (let [last-memories (vec (take-last 10 memories))
        prompt (generate-prompt
                (get-prompt (:prompt opts))
                last-memories keywords)

        for-conv (if (seq last-memories)
                   last-memories
                   [(first (shuffle (:imagine introductions)))])
        conv (format-for-completion for-conv)]
    (concat
     [{:role "system"
       :content prompt}]
     conv
     [{:role "user"
       :content (json/write-value-as-string
                 (select-keys request request-keys))}])))

(defn parse-number
  [s]
  (if (re-find #"^-?\d+\.?\d*$" s)
    (read-string s)))

(defn update-value
  "Updates the given key in the given map. Uses the given function to transform the value, if needed."
  [key transform-fn m default-value]
  (let [value (get m key)
        transformed-value (transform-fn value)]
    (assoc m key (if transformed-value
                   transformed-value
                   (or (transform-fn (str value))
                       default-value)))))

(def clean-energy
  (partial update-value :energy #(or (when (float? %) %) (parse-number (str %)))))

(def clean-emoji
  (partial update-value :emoji #(or (when (emoji/emoji? %) %)
                                    (:emoji (first (emoji/emoji-in-str %))))))

(defn clean-response
  [response]
  (-> response
      (clean-energy 0.5)
      (clean-emoji "🙃")))

(defn create-complete-fn
  [complete-fn]
  (fn [opts user request memories keywords]
    (let [submission (generate-submission opts request memories keywords)
          response (complete-fn user submission)
          cleaned-response (clean-response response)]
      (validate response-schema cleaned-response))))


(defmethod ig/init-key :ai.llm/complete-fn
[_ {:keys [implementation]
    :or   {implementation :openai}
    :as   opts}]
(create-complete-fn
 (case implementation
   :openai (openai/create-api-complete opts)
   :llama-shell (llama/create-complete-shell opts)
   )))
