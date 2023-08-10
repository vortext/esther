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

(defn push-memory
  [history-size new-memory memories]
  (conj (vec (take-last history-size memories)) new-memory))

(defn message [request]
  (let [history (get-in request [:session :history] [])
        response (converse/answer history request)
        new-history (push-memory 5 response history)
        energy (get-in response [:response :energy])
        response-msg (get-in response [:response :response])
        md (markdown/md-to-html-string response-msg)
        reply (ui
               [:div.memory
                {"data-energy" energy}
                [:div.request
                 (markdown/md-to-html-string
                  (get-in request [:params :msg]))]
                [:div.response md]])]
    (assoc reply :session {:history new-history})))



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
     "hx-on::before-request" "beforeConverseRequest()"
     "hx-on::after-request" "afterConverseRequest()"}
    [:input#user-input
     {:type "text"
      :autocomplete "off"
      :maxlength 240
      :autofocus "true"
      :placeholder "Dear Esther,"
      :name "msg"}]]])

(defn conversation [request]
  [:div.container
   [:div.row
    [:div.col-md-12]
    [:div#conversation.loading-state
     [:div#history]
     [:div#user-echo
      [:div#user-value {:class "user-message"}]]
     [:div#loading-response.loading-state
      loading]
     (msg-input request)]]])

(def ibm-plex "IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,200;0,400;0,500;1,400;1,500&display=swap")

(def noto-emoji "Noto+Emoji&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet" :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn head-section []
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
   ;; Math
   [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.8/katex.min.css"}]
   [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/KaTeX/0.16.8/katex.min.js"}]
   ;; Fonts
   (font-link ibm-plex)
   (font-link noto-emoji)
   [:link {:rel "stylesheet" :href "resources/public/fonts.css"}]
   [:link {:rel "stylesheet" :href "resources/public/main.css"}]
   [:title "Esther"]
   [:script {:src "https://unpkg.com/htmx.org@1.9.4"
             :integrity "sha384-zUfuhFKKZCbHTY6aRR46gxiqszMk5tcHjsVFxnUo8VMus4kHGVdIYVbOYYNlKmHV"
             :crossorigin "anonymous"}]
   [:script {:src "https://unpkg.com/hyperscript.org@0.9.5" :defer true}]
   [:script {:src "resources/public/js/main.js"}]])

(defn home [request]
  (page
   (head-section)
   [:body
    {"data-session-id" (random-base64)}
    [:h1#title "Esther"]
    [:h2#subtitle (time/human-today) "."]
    (conversation request)]))


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
