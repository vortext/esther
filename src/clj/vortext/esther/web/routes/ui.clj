(ns vortext.esther.web.routes.ui
  (:require
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.routes.utils :as utils]
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.htmx :refer [ui page] :as htmx]
   [integrant.core :as ig]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.logging :as log]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def loading
  [:div {:style "height: 5rem"
         :class "loadingio-spinner-typing-loading"}
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])



(defn message [request]
  (let [request (:params request)
        response (converse/process request)
        response-msg (get-in response [:response :response])]
    (ui
     [:div.memory
      [:hr]
      [:div.request (:msg request)]
      [:hr]
      [:div.response response-msg]])))

(defn msg-input [_request]
  [:div.input-form
   [:form
    {:id "message-form"
     :hx-post "/msg"
     :hx-swap "beforeend"
     :hx-boost "true"
     :hx-indicator ".loading-state"
     :hx-target "#history"
     :hx-trigger "submit"
     "hx-on::before-request" "let msg = document.querySelector('#user-input').value;
                              document.querySelector('#user-value').textContent = msg;
                              document.querySelector('#user-input').disabled = true;
                              document.querySelector('#user-input').value = '';"
     "hx-on::after-request" "document.querySelector('#user-input').disabled = false;
                             document.getElementById('user-input').focus();"}

    [:input#user-input {:type "text"
                        :name "msg"}]]])

(defn conversation [request]
  [:div.container
   [:div.row
    [:div.col-md-12]
    [:div#conversation.loading-state
     [:div#history]
     [:div#user-echo
      [:hr]
      [:div#user-value {:class "user-message"}]
      [:hr]] ;; Here's the #message element
     [:div#loading-response.loading-state
      loading]
     (msg-input request)
     ]]])

(defn home [request]
  (page
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:link {:rel "stylesheet" :href "https://gitcdn.link/repo/Chalarangelo/mini.css/master/dist/mini-default.min.css"}]
    [:link {:rel "stylesheet" :href "resources/public/main.css"}]
    [:title "Esther"]
    [:script {:src "https://unpkg.com/htmx.org@1.9.4"
              :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
              :crossorigin "anonymous"}]
    [:script {:src "https://unpkg.com/hyperscript.org@0.9.5" :defer true}]

    ]
   [:body
    (conversation request)
    ]))


;; Routes
(defn ui-routes [_opts]
  [["/" {:get home}]
   ["/msg" {:post message}]])

(def route-data
  {:muuntaja   formats/instance
   :middleware
   [;; Default middleware for ui
    ;; query-params & form-params
    parameters/parameters-middleware
    ;; encoding response body
    muuntaja/format-response-middleware
    ;; exception handling
    exception/wrap-exception]})

(derive :reitit.routes/ui :reitit/routes)

(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui-routes opts)])
