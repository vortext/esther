(ns vortext.esther.web.controllers.command
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.common :as common]
   [vortext.esther.errors :refer [wrapped-error]]
   [vortext.esther.util.markdown :as markdown]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.login :as login-ui]
   [vortext.esther.web.ui.memory :as memory-ui]))


(defn ->event
  [type content]
  {:event/role :system
   :event/content {:ui/type type
                   :content content}})


(defn status
  [_opts user _args _obj]
  (->event
    :htmx
    [:div.status
     [:pre
      [:strong "status: "] "ok"
      [:br]
      [:strong "username: "] (:username user)]]))


(defn inspect
  [opts user _args _obj]
  (let [memories (memory/recent-conversation opts user)]
    (->event
      :md-mono
      (if (seq memories)
        (str
          "#### memories"
          (memory-ui/md-memories-table memories))
        "**void**"))))


(defn keywords
  [opts user _args _obj]
  (let [keywords (memory/frecency-keywords opts user :week 10)]
    (->event
      :md-mono
      (if-not (seq keywords)
        "**empty**"
        (str
          "#### keywords"
          (memory-ui/md-keywords-table keywords))))))


(defn imagine
  [opts user _args _obj]
  (let [memories (memory/recent-conversation opts user)
        image #(-> % :memory/events second :event/content :imagination)
        fantasies (keep image memories)]
    (->event
      :md-mono
      (if (seq fantasies)
        (markdown/strs-to-markdown-list fantasies)
        "**nothing**"))))


(defn logout
  [_opts _user _args _obj]
  (->event :htmx (login-ui/logout-chat)))


(defn wipe
  [opts user args _obj]
  (->event :htmx (memory-ui/wipe-form opts user args)))


(defn archive
  [opts user _args _obj]
  (->event :htmx (memory-ui/archive-form opts user)))


(defn command!
  [opts user obj]
  (let [msg (common/request-msg obj)
        commands {:inspect inspect
                  :keywords keywords
                  :status status
                  :wipe wipe
                  :imagine imagine
                  :archive archive
                  :logout logout}
        [cmd args] (common/split-first-word
                    (apply str (rest msg)))]
    (if-let [impl (get commands (keyword cmd))]
      (impl opts user args obj)
      (wrapped-error
       :invalid-command
       (Exception. (str "Invalid command: " msg))))))
