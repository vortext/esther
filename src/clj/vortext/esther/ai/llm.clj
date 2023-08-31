(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [vortext.esther.web.controllers.memory :refer [extract-keywords]]
   [vortext.esther.util :refer [strs-to-markdown-list]]
   [jsonista.core :as json]
   [clojure.set :as set]
   [diehard.core :as dh]
   [vortext.esther.ai.llama :as llama]
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

(defn generate-submission
  [opts request memories keywords]
  (let [last-memories (vec (take-last 5 memories))
        prompt (generate-prompt
                (slurp (io/resource (:prompt opts)))
                last-memories keywords)

        for-conv (if (seq last-memories)
                   last-memories
                   [(rand-nth (:first-time introductions))])
        conv (format-for-completion for-conv)]
    (concat
     [{:role "system"
       :content prompt}]
     conv
     [{:role "user"
       :content (json/write-value-as-string
                 (select-keys request request-keys))}])))

(defn create-complete-fn
  [llm-complete]
  (fn [opts user request memories keywords]
    (dh/with-retry
      {:retry-on Exception
       :max-retries 1
       :on-retry
       (fn [_val ex] (log/warn "llm::complete:retrying..." ex))
       :on-failure
       (fn [_val ex]
         (let [response (:internal-server-error errors)]
           (log/warn "llm::complete:failed..." ex response)
           response))
       :on-failed-attempt
       (fn [_ _] (log/warn "llm::complete:failed-attempt..."))}
      (dh/with-timeout {:timeout-ms 60000}
        ((:complete-fn llm-complete)
         user
         (generate-submission opts request memories keywords))))))


(defmethod ig/init-key :ai.llm/llm-complete
  [_ {:keys [impl]
      :or   {impl :openai}
      :as   opts}]
  (let [instance
        (case impl
          :llama-shell (llama/create-complete-shell opts))]
    {:impl instance
     :complete-fn  (create-complete-fn instance)}
    ))


(defmethod ig/halt-key! :ai.llm/llm-complete [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
