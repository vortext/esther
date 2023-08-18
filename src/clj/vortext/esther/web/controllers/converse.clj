(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.config :refer [errors]]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.memory :as memory-ui]
   [vortext.esther.web.ui.signin :as signin-ui]
   [malli.core :as m]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util :refer [read-json-value]]
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
                         (memory/last-memories opts user 10))
        first-image (memory/first-image memories)]
    {:type :md-mono
     :response
     (str
      "#### scene "
      "\n\n"
      first-image
      "\n\n"
      "#### memories"
      (memory-ui/md-memories-table
       (take 5 memories))
      "#### keywords"
      (memory-ui/md-keywords-table
       (memory/frecency-keywords opts user :week 10)))}))

(defn logout
  [_opts _user _sid _args {:keys [request]}]
  {:type :htmx
   :response (signin-ui/logout-chat request)})

(defn wipe
  [opts user sid args {:keys [_request]}]
  {:type :htmx
   :response (memory-ui/wipe-form opts user sid args)})


(defn archive
  [opts user sid _args {:keys [_request]}]
  {:type :htmx
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
                  :archive archive
                  :logout logout}
        [cmd args] (split-first-word
                    (apply str (rest command)))
        response (if-let [impl (get commands (keyword cmd))]
                   (impl opts user sid args data)
                   (:invalid-command errors))]
    (-> data (assoc :response response))))

(defn converse!
  [opts user _sid data]
  (let [conversation (filter
                      (comp :conversation? :response)
                      (memory/last-memories opts user 10))
        last-memories (reverse conversation)
        keyword-memories (memory/frecency-keywords opts user :week 25)
        result (openai/complete
                opts
                last-memories
                keyword-memories
                (:request data))]
    (-> data
        (assoc
         :response
         (-> result
             (assoc :conversation? true)
             (assoc :type :md-serif))))))

(defn get-context
  [request]
  (read-json-value (get-in request [:params :context] "")))

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


(defn answer!
  [opts request]
  (let [{:keys [params]} request
        user (get-in request [:session :user])
        sid (:sid params)
        msg (emoji/parse-to-unicode
             (get-in request [:params :msg]))
        request {:context (get-context request)
                 :msg msg}
        data {:request request
              :ts (unix-ts)}]
    (if-not (m/validate request-schema request)
      (:unrecognized-input errors)
      (let [memory (respond! opts user sid data)
            type (keyword (:type (:response  memory)))]
        (if-not (= type :htmx)
          (memory/remember! opts user sid memory)
          memory)))))

;;; Scratch
