(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [vortext.esther.web.controllers.memory :refer
    [extract-keywords]]
   [vortext.esther.util :refer [strs-to-markdown-list]]
   [jsonista.core :as json]
   [clojure.set :as set]
   [malli.core :as m]
   [vortext.esther.util.emoji :as emoji]
   [malli.error :as me]
   [vortext.esther.config :refer [examples errors introductions]]))

(def scenarios
  {:converse (slurp (io/resource "prompts/scenarios/converse.md"))})

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
  [scenario memories keywords]
  (let [example (first (shuffle examples))
        memory-keywords (relevant-keywords memories keywords)
        conversation-keywords (extract-keywords memories)
        relevant-keywords (set/difference
                           memory-keywords
                           conversation-keywords)]
    (log/debug "openai::generate-prompt::relevant-keywords" relevant-keywords)
    (mustache/render
     scenario
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

(defn conversation-seq
  [user assistant memories]
  (interleave
   (map user (map :request memories))
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
      (log/warn "openai::validate:error" humanized obj)
      (-> (:validation-error errors)
          (assoc :details humanized)))
    ;; Valid
    obj))

(defn generate-submission
  [memories keywords request]
  (let [last-memories (vec (take-last 10 memories))

        scenario (:converse scenarios)
        prompt (generate-prompt scenario last-memories keywords)

        for-conv (if (seq last-memories)
                   last-memories
                   [(first (shuffle (:imagine introductions)))])
        conv (format-for-completion for-conv)]
    (concat
     [{:role "system"
       :content prompt}]
     conv
     [{:role "user"
       :content (json/write-value-as-string request)}])))

(defn submission-str
  [submission]
  (let [with-name #(if (= % "assistant") "esther" %)]
    (str/join
     "\n\n"
     (map #(str (with-name (:role %)) ": " (:content %)) submission))))

(defn complete
  [impl memories keywords request]
  (let [submission (generate-submission memories keywords request)]
    (log/debug "llm::complete:submission" (submission-str submission))
    (validate response-schema (impl submission))))
