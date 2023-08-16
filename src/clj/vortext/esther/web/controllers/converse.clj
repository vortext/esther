(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.web.middleware.auth :as auth]
   [vortext.esther.config :refer [errors]]
   [camel-snake-kebab.core :as csk]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.memory :refer [keywords-table memories-table]]
   [vortext.esther.web.ui.signin :as signin]

   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util :refer [read-json-value]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))


(defn remember!
  [opts user sid answer]
  (let [response (:response answer)
        keywords (map (fn [kw] (csk/->kebab-case (str/trim kw)))
                      (get response :keywords []))
        _ (log/debug "converse::remember![sid,keywords]" sid keywords)]
    (memory/remember! opts user sid answer keywords)))


(defn complete!
  [opts user sid data]
  (let [last-memories (memory/last-memories opts user)
        last-memories (reverse last-memories)
        keyword-memories (memory/frecency-keywords opts user)
        result (openai/complete
                opts
                last-memories
                keyword-memories
                (:request data))
        answer (-> data (assoc :response result))]
    (remember! opts user sid answer)))

(defn status
  [_opts user _sid _data]
  {:type :htmx
   :response
   [:div.status
    [:pre [:strong "username: "] (:username user)]]})

(defn inspect
  [opts user _sid _data]
  {:type :md-mono
   :response
   (str
    "**memories**"
    (memories-table (memory/last-memories opts user 5))
    "**keywords**"
    (keywords-table (memory/frecency-keywords opts user)))})

(defn logout
  [_opts _user _sid {:keys [request]}]
  {:type :htmx
   :response (signin/logout-chat request)})


(defn split-first-word [s]
  (let [[_ first-word rest] (re-matches #"(\S+)\s*(.*)" s)]
    [first-word (or rest "")]))

(defn command
  [opts user sid data]
  (let [command (get-in data [:request :msg])
        commands {:inspect inspect
                  :status status
                  :logout logout}
        [cmd _msg] (split-first-word
                    (apply str (rest command)))]
    (if-let [impl (get commands (keyword cmd))]
      (impl opts user sid data)
      (:invalid-command errors))))

(defn get-context
  [request]
  (read-json-value
   (get-in request [:params :context] "")))

(defn answer!
  [opts request]
  (when-let [_ (auth/authenticated? request)]
    (let [{:keys [params]} request
          user (get-in request [:session :user])
          sid (:sid params)
          request (assoc params :context (get-context request))
          data {:request request
                :ts (unix-ts)}]
      (try
        (if (str/starts-with? (:msg params) "/")
          (-> data
              (assoc :response (command opts user sid data)))

          ;; Converse
          (-> (complete! opts user sid data)
              (assoc-in [:response :type] :md-serif)))
        (catch Exception e
          (do (log/warn e)
              (assoc data :response (:internal-server-error errors))))))))

;;; Scratch
