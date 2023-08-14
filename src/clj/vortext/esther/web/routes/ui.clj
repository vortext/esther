(ns vortext.esther.web.routes.ui
  (:require
   [clojure.tools.logging :as log]
   [ring.util.response :as response]
   [jsonista.core :as json]
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.middleware.auth :as auth]
   [vortext.esther.util.security :refer [random-base64]]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.web.ui.conversation :as conversation]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [buddy.auth.accessrules :refer [wrap-access-rules]]
   [integrant.core :as ig]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def default-location "/converse")

(def ibm-plex "IBM+Plex+Mono&family=IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,300;0,400;0,500;1,400;1,500&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet" :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn json-config
  []
  (let [sid (random-base64 10)]
    [:script {:type "text/javascript"}
     (str "window.appConfig = "
          (json/write-value-as-string
           {"sid" sid
            "defaultLocation" default-location}) ";")]))

(defn head-section [scripts]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title "Esther"]
   (json-config)
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
   ;; Fonts
   (font-link ibm-plex)
   [:link {:rel "stylesheet" :href "resources/public/main.css"}]

   [:script {:src "https://unpkg.com/htmx.org@1.9.4"
             :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
             :crossorigin "anonymous"}]

   ;; Scripts
   (concat scripts)])


(defn sign-in
  [_ _request error-message]
  (page
   (head-section
    [[:script {:src "resources/public/js/login.js"}]])
   [:form {:action "/login"
           :method "POST"}
    (when error-message
      [:div.error error-message])
    [:label "Username: "
     [:input {:type "text" :name "username"}]]
    [:label "Password: "
     [:input {:type "password" :name "password"}]]
    [:button "Sign In"]]))


(defn login-handler [opts request]
  (if-let [uid (auth/authenticate opts request)]
    (do (log/info "authenticate uid " uid "redirecting to " default-location)
        {:status 303
         :session {:identity uid}
         :headers {"Location" default-location}})
    (sign-in request opts "Invalid credentials")))

(defn display-page [page-html-fn opts request]
  (->
   (page
    (head-section
     [[:script {:src "https://cdnjs.cloudflare.com/ajax/libs/suncalc/1.8.0/suncalc.min.js"}]
      [:script {:src "resources/public/js/main.js"}]])
    (page-html-fn opts request))))

;; Routes
(defn ui-routes [opts]
  [["/" {:get (fn [req] (if (auth/authenticated? req)
                         (response/redirect default-location)
                         (response/redirect "/login")))}]
   ["/login"
    {:post (partial login-handler opts)
     :get (fn [req] (sign-in opts req nil))}]
   ["/converse"
    {:get
     (fn [req]
       (display-page conversation/conversation-body opts req))}]
   ["/converse/msg"
    {:post (partial conversation/message opts)}]])


(defn on-error
  [req _]
  (log/warn
   "access-rules on-error" " session:" (:session req))
  {:status 303
   :headers {"Location" "/login"}
   :body "Redirecting to login"})

(defn any-access [_] true)

(def access-rules [{:pattern #"/$"
                    :handler any-access}
                   {:pattern #"^/login$"
                    :handler any-access}
                   {:pattern #"^/converse.*"
                    :handler auth/authenticated-access}])


(def route-data
  {:muuntaja   formats/instance
   :middleware
   [ ;; Default middleware for ui
    ;; query-params & form-params
    parameters/parameters-middleware
    ;; encoding response body
    muuntaja/format-response-middleware
    ;; exception handling
    exception/wrap-exception
    ;; buddy security
    [wrap-access-rules {:rules access-rules
                        :on-error on-error}]
    [wrap-authorization auth/auth-backend]
    [wrap-authentication auth/auth-backend]]})

(derive :reitit.routes/ui :reitit/routes)

(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui-routes opts)])
