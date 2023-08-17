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
   [clojure.string :as str]
   [clojure.tools.logging :as log]))


(defn complete!
  [opts user sid data]
  (let [last-memories (reverse (memory/last-memories opts user 10))
        keyword-memories (memory/frecency-keywords opts user)
        result (openai/complete
                opts
                last-memories
                keyword-memories
                (:request data))]
    (memory/remember!
     opts user sid
     (-> data (assoc :response result)))))

(defn status
  [_opts user _sid _args _data]
  {:type :htmx
   :response
   [:div.status
    [:pre
     [:strong "status: "] "ok"
     [:br]
     [:strong "username: "] (:username user)]]})

(def lambda
  {:week  1.6534e-6
   :day   1.1574e-5
   :hour  2.7701e-4
   :month 5.5181e-7})


(defn inspect
  [opts user _sid _args _data]
  {:type :md-mono
   :response
   (str
    "**memories**"
    (memory-ui/md-memories-table
     (memory/last-memories opts user 5))
    "**keywords**"
    (memory-ui/md-keywords-table
     (memory/frecency-keywords opts user (:week lambda) 10)))})

(defn logout
  [_opts _user _sid _args {:keys [request]}]
  {:type :htmx
   :response (signin-ui/logout-chat request)})

(defn clear
  [opts user sid args {:keys [_request]}]
  {:type :htmx
   :response (memory-ui/clear-form opts user sid args)})

(defn split-first-word [s]
  (let [[_ first-word rest] (re-matches #"(\S+)\s*(.*)" s)]
    [first-word (or rest "")]))

(defn command
  [opts user sid data]
  (let [command (get-in data [:request :msg])
        commands {:inspect inspect
                  :status status
                  :clear clear
                  :logout logout}
        [cmd args] (split-first-word
                    (apply str (rest command)))]
    (if-let [impl (get commands (keyword cmd))]
      (impl opts user sid args data)
      (:invalid-command errors))))

(defn get-context
  [request]
  (read-json-value (get-in request [:params :context] "")))

(defn- respond!
  [opts user sid data]
  (try
    (if (str/starts-with? (get-in data [:request :msg]) "/")
      (-> data
          (assoc :response (command opts user sid data)))

      ;; Converse
      (-> (complete! opts user sid data)
          (assoc-in [:response :type] :md-serif)))
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
        request {:context (get-context request)
                 :msg (get-in request [:params :msg])}

        data {:request request
              :ts (unix-ts)}]
    (if-not (m/validate request-schema request)
      (:unrecognized-input errors)
      (respond! opts user sid data))))

;;; Scratch
