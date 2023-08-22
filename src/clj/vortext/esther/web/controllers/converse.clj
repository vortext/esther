(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.config :refer [errors]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.memory :as memory-ui]
   [vortext.esther.web.ui.signin :as signin-ui]
   [malli.core :as m]
   [malli.error :as me]
   [vortext.esther.util :refer [read-json-value strs-to-markdown-list]]
   [vortext.esther.util.emoji :as emoji]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defn status
  [_opts user _sid _args _data]
  {:type :htmx
   :response
   [:div.status
    [:pre
     [:strong "status: "] "ok"
     [:br]
     [:strong "username: "] (:username user)]]})

(defn inspect
  [opts user _sid _args _data]
  (let [memories (filter (comp :conversation? :response)
                         (memory/last-memories opts user 10))]
    {:type :md-mono
     :response
     (str
      "#### memories"
      (memory-ui/md-memories-table
       (take 5 memories))
      "#### keywords"
      (memory-ui/md-keywords-table
       (memory/frecency-keywords opts user :week 10)))}))

(defn imagine
  [opts user _sid _args _data]
  (let [memories (filter (comp :conversation? :response)
                         (memory/last-memories opts user 10))]
    {:type :md-mono
     :response
     (strs-to-markdown-list
      (map #(get-in % [:response :image-prompt])
           (take 3 memories)))}))

(defn logout
  [_opts _user _sid _args {:keys [request]}]
  {:type :ui
   :response (signin-ui/logout-chat request)})

(defn wipe
  [opts user sid args {:keys [_request]}]
  {:type :ui
   :response (memory-ui/wipe-form opts user sid args)})


(defn archive
  [opts user sid _args {:keys [_request]}]
  {:type :ui
   :response (memory-ui/archive-form opts user sid)})

(defn split-first-word [s]
  (let [[_ first-word rest] (re-matches #"(\S+)\s*(.*)" s)]
    [first-word (or rest "")]))

(defn command!
  [opts user sid data]
  (let [command (get-in data [:request :msg])
        commands {:inspect inspect
                  :status status
                  :wipe wipe
                  :imagine imagine
                  :archive archive
                  :logout logout}
        [cmd args] (split-first-word
                    (apply str (rest command)))
        response (if-let [impl (get commands (keyword cmd))]
                   (impl opts user sid args data)
                   (:invalid-command errors))]
    (-> data (assoc :response response))))


(def response-schema
  [:map
   [:response
    [:and
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
      (log/warn "llm::validate:error" obj humanized)
      (-> (:validation-error errors)
          (assoc :details humanized)))
    ;; Valid
    obj))

(defn parse-number
  [s]
  (when (re-find #"^-?\d+\.?\d*$" s)
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
  (partial
   update-value :energy
   #(let [parsed-val
          (or (when (and (float? %) (<= 0 % 1)) %)
              (parse-number (str %)))]
      (when (and parsed-val (<= 0 parsed-val 1))
        (min 0.99 (float parsed-val))))))

(def clean-emoji
  (partial
   update-value :emoji
   #(or (when (emoji/emoji? %) %)
        (:emoji (first (emoji/emoji-in-str
                        (emoji/parse-to-unicode %)))))))

(defn clean-response
  [response]
  (-> response
      (clean-energy 0.5)
      (clean-emoji "🙃")))

(defn converse!
  [opts user _sid data]
  (let [conversation (filter
                      (comp :conversation? :response)
                      (memory/last-memories opts user 5))
        last-memories (reverse conversation)
        keyword-memories (memory/frecency-keywords opts user :week 25)
        complete (get-in opts [:ai :complete-fn])
        ;; The actual LLM complete
        response (complete opts user (:request data) last-memories keyword-memories)
        validate-response #(validate response-schema %)]
    (-> data
        (assoc
         :response
         (-> response
             (clean-response)
             (validate-response)
             (assoc :conversation? true)
             (assoc :type :md-serif))))))

(defn- respond!
  [opts user sid data]
  (try
    (if (str/starts-with? (get-in data [:request :msg]) "/")
      (command! opts user sid data)
      (converse! opts user sid data))
    (catch Exception e
      (do (log/warn e)
          (assoc data :response (:internal-server-error errors))))))

(def request-schema
  [:map
   [:msg [:and
          [:string {:min 1, :max 1024}]
          [:fn {:error/message "msg should be at most 1024 chars"}
           (fn [s] (<= (count s) 1024))]]]
   [:context [:map {:optional true}]]])

(defn parse-context
  [context-str]
  (read-json-value context-str))

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        user (get-in request [:session :user])
        sid (:sid params)
        data {:request
              {:context (parse-context (get params :context ""))
               :msg (emoji/parse-to-unicode (:msg params))}
              :ts (unix-ts)}]
    (if-not (m/validate request-schema (:request data))
      (assoc data :response (:unrecognized-input errors))
      (let [memory (respond! opts user sid data)
            type (keyword (:type (:response  memory)))]
        (if-not (= type :ui)
          (memory/remember! opts user sid memory)
          memory)))))

;;; Scratch
