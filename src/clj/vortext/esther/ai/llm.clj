(ns vortext.esther.ai.llm
  (:require
   [vortext.esther.util.mustache :as mustache]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [diehard.core :as dh]
   [vortext.esther.ai.llama :as llama]
   [vortext.esther.config :refer [errors]]))


(defn generate-prompt
  [prompt]
  (mustache/render prompt {}))

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
  [opts request memories]
  (let [last-memories (vec (take-last 5 memories))
        prompt (generate-prompt
                (slurp (io/resource (:prompt opts))))
        conv (format-for-completion last-memories)]
    (concat
     [{:role "system"
       :content prompt}]
     conv
     [{:role "user"
       :content (json/write-value-as-string
                 (select-keys request request-keys))}])))

(defn create-complete-fn
  [llm-complete]
  (fn [opts user request memories]
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
         (generate-submission opts request memories))))))


(defmethod ig/init-key :ai.llm/llm-complete
  [_ {:keys [impl]
      :as   opts}]
  (let [instance
        (case impl
          :llama-shell (llama/create-complete-shell opts))]
    {:impl instance
     :complete-fn  (create-complete-fn instance)}))


(defmethod ig/halt-key! :ai.llm/llm-complete [_ {:keys [impl]}]
  ((:shutdown-fn impl)))
