(ns vortext.esther.ai.stablediffusion
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [clj-http.client :as client]
   [diehard.core :as dh]
   [jsonista.core :as json]
   [vortext.esther.secrets :refer [secrets]]))

(defonce api-key (:stablediffusion-api-key (secrets)))
(defonce url "https://stablediffusionapi.com/api/v4/dreambooth")

(defn fetch [request-id]
  (let [url (str url "/fetch")
        headers {"Content-Type" "application/json"}
        body (json/write-value-as-string {:key api-key
                                          :request_id request-id})
        options {:body body
                 :headers headers
                 :content-type :json
                 :follow-redirects true
                 :as :json}]
    (try
      (let [result (:body (client/post url options))]
        (when (= (:status result) "success")
          (:output result)))
      (catch Exception e
        (log/warn "error" (.getMessage e))))))


(def example "An image of a cheerful person with a book in their hands, symbolizing the joy of sharing stories and knowledge.")

(defn request-body
  [prompt]
  {:model_id "midjourney",
   :key api-key,
   :prompt prompt
   :width "1024"
   :height "768",
   :negative_prompt "",
   :safety_checker "no"
   :num_inference_steps "42",
   :samples "1",
   :guidance_scale 7.5})

(defn process-response
  [body]
  (if-not (:output body)
    (if-let [new-output (fetch (:id body))]
      (assoc body :output new-output)
      (do (Thread/sleep 1000)
          (log/warn "retrying fetch" (:id body))
          (process-response body)))
    body))

(defn imagine-async
  [prompt callback]
  (let [request {:headers {"Content-Type" "application/json"}
                 :body (json/write-value-as-string (request-body prompt))
                 :as :json
                 :content-type :json
                 :follow-redirects true}
        request-fn (fn [request]
                     (dh/with-retry {:max-retries 3}
                       (client/post url request)))]
    (future (-> (request-fn request) :body process-response callback))))
