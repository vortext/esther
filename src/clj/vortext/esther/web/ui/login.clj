(ns vortext.esther.web.ui.login
  (:require
    [clojure.tools.logging :as log]
    [vortext.esther.web.htmx :refer [page ui] :as htmx]
    [vortext.esther.web.middleware.auth :refer [authenticate authenticated?]]
    [vortext.esther.web.ui.common :as common]))


(defn logout-chat
  []
  [:form {:action "/logout" :method :post}
   [:button.button.button-primary
    {:name "logout" :value -1} "Click to logout"]])


(def includes
  {:styles  ["css/login.css"]
   :scripts ["js/login.js"]})

(defn render
  [{:keys [default-path]} request]
  (let [config {:redirect default-path}]
    (if (authenticated? request)
      {:status 303
       :headers {"Location" default-path}}
      (page
       (common/head
        (assoc includes :config config))
       [:body
        [:div.container
         [:div.login-box
          [:h1 "Esther"]
          [:div#error-message.error]
          [:form#login-form
           {:hx-post "/login"
            :hx-target "#error-message"}
           [:div.form-group
            [:label "Username"
             [:input {:type "text" :name "username" :class "form-input"}]]]
           [:div.form-group
            [:label "Password"
             [:input {:type "password" :name "password" :class "form-input"}]]]
           [:button#submit.button.button-primary.action
            {:onclick "handleClick(this)"}
            [:span.sign-in-text "Sign In"]
            [:img.htmx-indicator
             {:src "/resources/public/img/3-dots-fade.svg"}]]]]]]))))


(defn handler
  [{:keys [default-path] :as opts} request]
  (if-let [user (authenticate opts request)]
    {:status 200
     :session {:identity (get-in user [:vault :uid])
               :user user}
     :headers {"HX-Redirect" default-path}}
    (ui [:span.login-failed "Invalid credentials"])))
