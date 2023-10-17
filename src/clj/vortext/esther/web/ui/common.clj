(ns vortext.esther.web.ui.common
  (:require
   [jsonista.core :as json]))


(defn client-config
  [config]
  [:script {:type "text/javascript"}
   (str "window.clientConfig = "
        (json/write-value-as-string
         (or config {})) ";")])


(def default-styles
  ["css/fonts.css"
   "css/main.css"])


(def default-scripts
  ["js/vendor/htmx.js"])


(defn head
  [{:keys [config styles scripts]}]
  (let [base-dir "/resources/public/"
        all-scripts (concat default-scripts (or scripts []))
        all-styles (concat default-styles (or styles []))]
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Esther"]
     (client-config config)
     (concat (for [css all-styles]
               [:link {:rel "stylesheet"
                       :href (str base-dir css)}]))
     (concat (for [js all-scripts]
               [:script {:src (str base-dir js)}]))]))
