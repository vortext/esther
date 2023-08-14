(ns vortext.esther.web.ui.common
  (:require
   [vortext.esther.util.security :refer [random-base64]]
   [jsonista.core :as json]))

(def ibm-plex "IBM+Plex+Mono&family=IBM+Plex+Sans:ital,wght@0,400;0,500;1,400;1,500&family=IBM+Plex+Serif:ital,wght@0,300;0,400;0,500;1,400;1,500&display=swap")

(defn font-link [font-param]
  [:link {:rel "stylesheet"
          :href (str "https://fonts.googleapis.com/css2?family=" font-param)}])

(defn json-config
  [config]
  (let [sid (random-base64 10)
        cfg {:sid sid}]
    [:script {:type "text/javascript"}
     (str "window.appConfig = "
          (json/write-value-as-string
           (merge cfg (or config {}))) ";")]))

(defn head
  [config styles scripts]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title "Esther"]
   (json-config config)
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin "true"}]
   ;; Fonts
   (font-link ibm-plex)

   ;; Styles
   [:link {:rel "stylesheet" :href "/resources/public/css/main.css"}]
   (concat styles)

   [:script {:src "/resources/public/js/vendor/htmx.min.js"}]

   ;; Scripts
   (concat scripts)])
