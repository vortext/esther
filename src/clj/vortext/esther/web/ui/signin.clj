(ns vortext.esther.web.ui.signin
  (:require
   [vortext.esther.web.ui.common :as common]
   [clojure.tools.logging :as log]
   [vortext.esther.web.htmx :refer [page] :as htmx]
   [vortext.esther.web.middleware.auth :refer [authenticate]]))


(defn render
  [_ _request error-message]
  (page
   (common/head {} [] [])
   [:form {:action "/login"
           :method "POST"}
    (when error-message
      [:div.error error-message])
    [:label "Username: "
     [:input {:type "text" :name "username"}]]
    [:label "Password: "
     [:input {:type "password" :name "password"}]]
    [:button "Sign In"]]))


(defn login-handler [{:keys [default-path] :as opts} request]
  (if-let [uid (authenticate opts request)]
    (do (log/info "authenticate uid " uid "redirecting to " default-path)
        {:status 303
         :session {:identity uid}
         :headers {"Location" default-path}})
    (render opts request "Invalid credentials")))
