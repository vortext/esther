(ns vortext.esther.web.ui.common
  (:require
   [vortext.esther.util :refer [random-base64]]
   [jsonista.core :as json]))

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

   ;; Styles
   [:link {:rel "stylesheet" :href "/resources/public/css/fonts.css"}]
   [:link {:rel "stylesheet" :href "/resources/public/css/main.css"}]
   (concat styles)

   [:script {:src "/resources/public/js/vendor/htmx.min.js"}]

   ;; Scripts
   (concat scripts)])
