(ns vortext.esther.web.routes.ui
  (:require
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.htmx :refer [ui page] :as htmx]
   [vortext.esther.util.time :as time]
   [markdown.core :as markdown]
   [vortext.esther.util.security :refer [random-base64]]

   [integrant.core :as ig]
   [clojure.tools.logging :as log]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def loading
  [:div.esther-typing-loading
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])

(defn markdown->html [md-text]
  (markdown/md-to-html-string md-text))

(defn message [opts request]
  (let [response (converse/answer! opts request)
        _ (log/debug "ui::mesage:request" response)
        _ (log/debug "ui::mesage:response" response)
        energy (get-in response [:response :energy])
        response-type (get-in response [:response :type] :esther)
        response-msg (get-in response [:response :response])
        md-response (markdown->html response-msg)
        md-request (markdown->html (get-in request [:params :msg]))]
    (ui
     [:div.memory
      {"data-energy" energy}
      [:div.request md-request]
      [:div.response {:class (name response-type)} md-response]])))

(defn msg-input [_request]
  [:div.input-form
   [:form
    {:id "message-form"
     :hx-post "/msg"
     :hx-swap "beforeend settle:0.25s"
     :hx-boost "true"
     :hx-indicator ".loading-state"
     :hx-target "#history"
     :hx-trigger "submit"
     "hx-on::before-request" "beforeConverseRequest();"
     "hx-on::after-request" "afterConverseRequest();"}
    [:input#user-context
     {:type "hidden"
      :name "context"
      :value "{}"}]
    [:textarea#user-input
     {:autocomplete "off"
      :minlength 1
      :maxlength 1024
      :autofocus "true"
      :placeholder "Dear Esther,"
      :name "msg"
      :rows 1
      :onkeydown "handleTextareaInput(event);"}]]])

(defn conversation [request]
  [:div.container
   [:div#conversation.loading-state
    [:div#history]
    [:div#user-echo
     [:div#user-value {:class "user-message"}]]
    [:div#loading-response.loading-state
     loading]
    (msg-input request)]])


(def ibm-plex "IBM+Plex+Mono&family=IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,300;0,400;0,500;1,400;1,500&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet" :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn head-section []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
   ;; Fonts
   (font-link ibm-plex)
   [:link {:rel "stylesheet" :href "resources/public/main.css"}]
   [:title "Esther"]
   [:script {:src "https://unpkg.com/htmx.org@1.9.4"
             :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
             :crossorigin "anonymous"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/suncalc/1.8.0/suncalc.min.js "}]
   [:script {:src "resources/public/js/main.js"}]])

(defn home [request]
  (let [sid (random-base64 32)]
    (->
     (page
      (head-section)
      [:body
       {"data-sid" sid}
       [:h1#title "Esther"]
       [:h2#subtitle (time/human-today) "."]
       (conversation request)])
     (assoc-in [:session :sid] sid))))

;; Routes
(defn ui-routes [opts]
  [["/" {:get home}]
   ["/msg" {:post (partial message opts)}]])

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
