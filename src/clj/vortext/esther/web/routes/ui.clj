(ns vortext.esther.web.routes.ui
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.middleware.auth :as auth]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.web.ui.conversation :as conversation]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [buddy.auth.accessrules :refer [wrap-access-rules]]
   [integrant.core :as ig]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def ibm-plex "IBM+Plex+Mono&family=IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,300;0,400;0,500;1,400;1,500&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet" :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn head-section [maybe-user]
  [:head
   (concat
    [[:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Esther"]

     [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
     [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
     ;; Fonts
     (font-link ibm-plex)
     [:link {:rel "stylesheet" :href "resources/public/main.css"}]]

    ;; Scripts
    (if maybe-user
      [[:script {:src "https://unpkg.com/htmx.org@1.9.4"
                 :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
                 :crossorigin "anonymous"}]
       [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/suncalc/1.8.0/suncalc.min.js"}]
       [:script {:src "resources/public/js/main.js"}]]
      []))])

(defn sign-in
  [error-message]
  [:form {:hx-post "/login" :hx-swap "outerHTML"}
   (when error-message
     [:div.error error-message])
   [:label "Username: "
    [:input {:type "text" :name "username"}]]
   [:label "Password: "
    [:input {:type "password" :name "password"}]]
   [:button "Sign In"]])

(defn content [opts _user request]
  (conversation/conversation-body opts request))

#_(defn login-handler [opts request]
    (if-let [token (auth/auth-handler opts request)]
      (do (log/info "auth-handler" token (keys opts) (keys request))
          (-> (ui (content opts request))
              (assoc :headers {"Authorization" (str "Bearer " token)})))

      (ui (sign-in "Invalid credentials"))))

;; Semantic response helpers
(defn ok [d] {:status 200 :body d})
(defn bad-request [d] {:status 400 :body d})


(defn login-handler [opts request]
  (if-let [user (auth/auth-handler opts request)]
    (do (log/info "auth-handler" user (keys opts) (keys request))
        (-> (ui (content opts user request))
            (assoc-in [:session :identity] user))) ; Use :identity key
    (ui (sign-in "Invalid credentials"))))


(defn home [opts request]
  (let [maybe-user (get-in request [:session :identity])]
    (log/debug "maybe" maybe-user)
    (page
     (head-section maybe-user)
     (if maybe-user
       (content opts maybe-user request)
       (sign-in nil)))))


;; Routes
(defn ui-routes [opts]
  [["/" {:get (partial home opts)}]
   ["/login" {:post (partial login-handler opts)}]
   ["/ui/msg" {:post (partial conversation/message opts)}]])


(defn on-error
  [request value]
  {:status 403
   :headers {}
   :body "Not authorized"})


(defn any-access [_] true)

(def access-rules [{:pattern #"/$"
                    :handler any-access}
                   {:pattern #"^/login$"
                    :handler any-access}
                   {:pattern #"^/ui/.*"
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
    [wrap-authentication auth/auth-backend]

    ]})

(derive :reitit.routes/ui :reitit/routes)

(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui-routes opts)])
