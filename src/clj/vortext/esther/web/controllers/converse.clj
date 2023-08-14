(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.web.middleware.auth :as auth]
   [vortext.esther.config :refer [errors]]
   [camel-snake-kebab.core :as csk]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.signin :as signin]
   [vortext.esther.ai.openai :as openai]
   [vortext.esther.util.security :refer [random-base64]]
   [vortext.esther.util :refer [read-json-value]]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [jsonista.core :as json]
   [clojure.tools.logging :as log]
   [table.core :as t]))


(defn get-context
  [request]
  (read-json-value
   (get-in request [:params :context] "")))

(defn remember!
  [opts uid sid answer]
  (let [response (:response answer)
        keywords (map (fn [kw] (csk/->kebab-case (str/trim kw)))
                      (get response :keywords []))
        _ (log/debug "converse::remember![gid,sid,keywords]" sid keywords)]
    (memory/remember! opts uid sid answer keywords)))

(defn contents-as-memories
  [jsons]
  (map (comp read-json-value :content) jsons))

(defn complete!
  [opts uid sid data]
  (try
    (let [last-10-memories (memory/last-memories opts uid)
          last-10-memories (reverse last-10-memories)
          result (openai/complete opts last-10-memories (:request data))
          answer (-> data
                     (assoc :response
                            (assoc result :type :md-serif)))]
      (log/debug "converse::complete!" uid sid answer)
      (remember! opts uid sid answer))
    (catch Exception e (log/warn "converse:complete" e)
           (:internal-server-error errors))))


(defn inspect
  [opts uid _sid _data]
  (let [memories (memory/last-memories opts uid 5)
        responses (map
                   (fn [memory]
                     (let [response (:response memory)
                           kw (:keywords response)]
                       (assoc
                        response :keywords
                        (clojure.string/join ", " kw))))
                   memories)
        ks [:emoji :energy :keywords :image-prompt]]
    (log/debug "converse::inspect")
    {:type :md-mono
     :response
     (t/table-str
      (map #(select-keys % ks) responses)
      :style :github-markdown)}))

(defn logout
  [_opts _uid _sid {:keys [request]}]
  {:type :htmx
   :response (signin/logout-chat request)})


(defn split-first-word [s]
  (let [[_ first-word rest] (re-matches #"(\S+)\s*(.*)" s)]
    [first-word (or rest "")]))

(defn command
  [opts uid sid data]
  (let [[cmd _msg] (split-first-word
                    (apply str (rest (:command? data))))

        impl {:inspect inspect
              :logout logout}]
    (((keyword cmd) impl) opts uid sid data)))

(defn answer!
  [opts request]
  (let [{:keys [params]} request
        uid (auth/authenticated? request)
        sid (:sid params)
        context (get-context request)
        request-with-context (assoc params :context context)
        msg-params (:msg params)
        command? (str/starts-with? msg-params "/")
        data {:request request-with-context
              :ts (unix-ts)}]
    (log/info "command?" command? msg-params)
    (try
      (if command?
        (assoc
         data :response
         (command opts uid sid (assoc data :command? msg-params)))
        ;; Converse
        (complete! opts uid sid data))
      (catch Exception e
        (do (log/warn e)
            (assoc data :response (:internal-server-error errors)))))))

;;; Scratch
