(ns vortext.esther.web.routes.ui
  (:require
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.htmx :refer [page] :as htmx]
   [vortext.esther.util.time :as time]
   [vortext.esther.util.security :refer [random-base64]]
   [vortext.esther.web.ui.conversation :as conversation]
   [integrant.core :as ig]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))

(def ibm-plex "IBM+Plex+Mono&family=IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,300;0,400;0,500;1,400;1,500&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet" :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn head-section []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title "Esther"]

   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
   ;; Fonts
   (font-link ibm-plex)
   [:link {:rel "stylesheet" :href "resources/public/main.css"}]

   ;; Scripts
   [:script {:src "https://unpkg.com/htmx.org@1.9.4"
             :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
             :crossorigin "anonymous"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/suncalc/1.8.0/suncalc.min.js"}]
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
       (conversation/conversation request)])
     (assoc-in [:session :sid] sid))))

;; Routes
(defn ui-routes [opts]
  [["/" {:get home}]
   ["/msg" {:post (partial conversation/message opts)}]])

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
