(ns vortext.esther.web.ui.signin
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.ui.common :as common]
   [vortext.esther.web.htmx :refer [page] :as htmx]
   [vortext.esther.web.middleware.auth :refer [authenticate authenticated?]]))

(defn logout-chat
  [_]
  [:form {:action "/logout" :method :post}
   [:button {:name "logout" :value -1} "Logout"]])

(defn render
  [{:keys [default-path]} request error-message]
  (if (authenticated? request)
    {:status 303
     :headers {"Location" default-path}}
    (page
     (common/head
      {}
      [[:link {:rel "stylesheet" :href "/resources/public/css/signin.css"}]]
      [[:script {:src "/resources/public/js/signin.js"}]])
     [:body
      [:div.container
       [:div.login-box
        [:h1 "Esther"]
        (when error-message
          [:div.error error-message])
        [:form {:action "/signin" :method "POST"}
         [:div.form-group
          [:label "Username: "
           [:input {:type "text" :name "username" :class "form-input"}]]]
         [:div.form-group
          [:label "Password: "
           [:input {:type "password" :name "password" :class "form-input"}]]]
         [:button "Sign In"]]]]])))


(defn handler [{:keys [default-path] :as opts} request]
  (if-let [user (authenticate opts request)]
    (do (log/info "signin::handler:authenticate " user "redirecting to " default-path)
        {:status 303
         :session {:identity (get-in user [:vault :uid])
                   :user user}
         :headers {"Location" default-path}})
    (render opts request "Invalid credentials")))
