(ns vortext.esther.web.ui.signin
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.ui.common :as common]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.web.middleware.auth :refer [authenticate authenticated?]]))


(defn render
  [{:keys [default-path]} request error-message]
  (if (authenticated? request)
    {:status 303
     :headers {"Location" default-path}}
    (page
     (common/head
      {}
      [[:link {:rel "stylesheet" :href "resources/public/css/signin.css"}]]
      [[:script {:src "resources/public/js/signin.js"}]])
     [:body
      [:div.container
       [:div.login-box
        [:h1 "Esther"]
        (when error-message
          [:div.error error-message])
        [:form {:action "/login" :method "POST"}
         [:div.form-group
          [:label "Username: "
           [:input {:type "text" :name "username" :class "form-input"}]]]
         [:div.form-group
          [:label "Password: "
           [:input {:type "password" :name "password" :class "form-input"}]]]
         [:button "Sign In"]]]]])))


(defn login-handler [{:keys [default-path] :as opts} request]
  (if-let [uid (authenticate opts request)]
    (do (log/info "authenticate uid " uid "redirecting to " default-path)
        {:status 303
         :session {:identity uid}
         :headers {"Location" default-path}})
    (render opts request "Invalid credentials")))
