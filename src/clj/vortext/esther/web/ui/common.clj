(ns vortext.esther.web.ui.common
  (:require
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [jsonista.core :as json]))


(def base-uri "/resources/public/")

(defn client-config
  [config]
  [:script {:type "text/javascript"}
   (str "window.clientConfig = "
        (json/write-value-as-string
         (or config {})) ";")])

(defn stylesheets
  [{:keys [files bundle]}]
  (if (io/resource (str "public/" bundle))
    [[:link {:rel "stylesheet"
             :href (str base-uri bundle)}]]
    ;; No bundle found, include files
    (for [style files]
      [:link {:rel "stylesheet"
              :href (str base-uri style)}])))


(defn scripts
  [{:keys [files bundle]}]
  (if (io/resource (str "public/" bundle))
    [[:script {:src (str base-uri bundle)}]]
    ;; No bundle found, include files
    (for [script files]
      [:script {:src (str base-uri script)}])))


(defn head
  [{:keys [config css js]}]
  [:head
   [:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:title "Esther"]
   (client-config config)
   (concat (stylesheets css))
   (concat (scripts js))])
