(ns vortext.esther.web.controllers.command
  (:require
   [vortext.esther.config :refer [errors]]
   [vortext.esther.common :as common]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.memory :as memory-ui]
   [vortext.esther.web.ui.login :as login-ui]
   [vortext.esther.util.markdown :as markdown]))

(defn status
  [_opts user _args _data]
  {:type :htmx
   :reply
   [:div.status
    [:pre
     [:strong "status: "] "ok"
     [:br]
     [:strong "username: "] (:username user)]]})

(defn inspect
  [opts user _args _data]
  {:type :md-mono
   :reply
   (str
    "#### memories"
    (memory-ui/md-memories-table
     (take 5 (filter (comp :conversation? :response)
                     (memory/last-memories opts user 10)))))})

(defn keywords
  [opts user _args _data]
  {:type :md-mono
   :reply
   (str
    "#### keywords"
    (memory-ui/md-keywords-table
     (memory/frecency-keywords opts user :week 10)))})

(defn imagine
  [opts user _args _data]
  (let [memories (filter (comp :conversation? :response)
                         (memory/last-memories opts user 10))]
    {:type :md-mono
     :reply
     (markdown/strs-to-markdown-list
      (map #(get-in % [:response :imagination])
           (reverse (take 3 memories))))}))

(defn logout
  [_opts _user _args {:keys [request]}]
  {:type :ui
   :reply (login-ui/logout-chat request)})

(defn wipe
  [opts user args {:keys [_request]}]
  {:type :ui
   :reply (memory-ui/wipe-form opts user args)})

(defn archive
  [opts user _args {:keys [_request]}]
  {:type :ui
   :reply (memory-ui/archive-form opts user)})


(defn command!
  [opts user data]
  (let [command (get-in data [:request :msg])
        commands {:inspect inspect
                  :keywords keywords
                  :status status
                  :wipe wipe
                  :imagine imagine
                  :archive archive
                  :logout logout}
        [cmd args] (common/split-first-word
                    (apply str (rest command)))
        response (if-let [impl (get commands (keyword cmd))]
                   (impl opts user args data)
                   (:invalid-command errors))]
    (-> data (assoc :response response))))
