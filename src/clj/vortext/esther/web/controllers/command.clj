(ns vortext.esther.web.controllers.command
  (:require
   [vortext.esther.config :refer [errors wrapped-error wrapped-response]]
   [vortext.esther.common :as common]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.memory :as memory-ui]
   [vortext.esther.web.ui.login :as login-ui]
   [vortext.esther.util.markdown :as markdown]))

(defn status
  [_opts user _args _obj]
  (wrapped-response
   :htmx
   [:div.status
    [:pre
     [:strong "status: "] "ok"
     [:br]
     [:strong "username: "] (:username user)]]))

(defn inspect
  [opts user _args _obj]
  (wrapped-response
   :md-mono
   (str
    "#### memories"
    (memory-ui/md-memories-table
     (take 5 (filter :memory/conversation?
                     (memory/last-memories opts user 10)))))))

(defn keywords
  [opts user _args _obj]
  (wrapped-response
   :md-mono
   (str
    "#### keywords"
    (memory-ui/md-keywords-table
     (memory/frecency-keywords opts user :week 10)))))

(defn imagine
  [opts user _args _obj]
  (let [memories (filter :memory/conversation?
                         (memory/last-memories opts user 10))]
    (wrapped-response
     :md-mono
     (markdown/strs-to-markdown-list
      (keep #(get-in % [:converse/response :imagination])
            (reverse (take 3 memories)))))))

(defn logout
  [_opts _user _args _obj]
  (wrapped-response :ui (login-ui/logout-chat)))

(defn wipe
  [opts user args _obj]
  (wrapped-response :ui (memory-ui/wipe-form opts user args)))

(defn archive
  [opts user _args _obj]
  (wrapped-response :ui (memory-ui/archive-form opts user)))


(defn command!
  [opts user obj]
  (let [content (get-in obj [:converse/request :content])
        commands {:inspect inspect
                  :keywords keywords
                  :status status
                  :wipe wipe
                  :imagine imagine
                  :archive archive
                  :logout logout}
        [cmd args] (common/split-first-word
                    (apply str (rest content)))]
    (if-let [impl (get commands (keyword cmd))]
      (impl opts user args obj)
      (wrapped-error :invalid-command nil))))
