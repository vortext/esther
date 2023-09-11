(ns vortext.esther.web.ui.common
  (:require
   [vortext.esther.util.zlib :as zlib]
   [babashka.process :refer [process shell]]
   [clj-commons.digest :as digest]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [babashka.fs :as fs]
   [jsonista.core :as json]))

(defn json-config
  [config]
  [:script {:type "text/javascript"}
   (str "window.appConfig = "
        (json/write-value-as-string
         (or config {})) ";")])

(def default-styles
  ["public/css/fonts.css"
   "public/css/main.css"])

(def default-scripts
  ["public/js/vendor/htmx.js"])

(defn ->canonical-path
  [resource]
  (str (fs/canonicalize (io/resource resource))))

(def minify-bin
  (fs/canonicalize (io/resource "scripts/minify/linux_amd64/minify")))

(defn minify
  [paths outfile]
  (let [cmd [(str minify-bin) "-b" "-o" outfile " " (str/join " " paths)]]
    (shell (str/join " " cmd)) outfile))

(defn bundle
  [out-dir resources prefix suffix]
  (let [paths (map ->canonical-path resources)
        hash (digest/md5 (apply str (map zlib/calculate-crc32 paths)))
        filename (str prefix hash suffix)
        outfile (str (fs/path out-dir filename))]
    (if (fs/exists? outfile) outfile (minify paths outfile))))

(defn head
  [config styles scripts]
  (let [out "public/assets"
        out-dir (fs/canonicalize (io/resource out))
        all-scripts (concat default-scripts scripts)
        all-styles (concat default-styles styles)
        bundle-asset
        (fn [& args]
          (str (fs/path
                "/resources" out
                (fs/file-name (apply (partial bundle out-dir) args)))))
        js-asset (bundle-asset all-scripts "main_" ".js")
        css-asset (bundle-asset all-styles "styles_" ".css")]
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Esther"]
     (json-config config)
     [:link {:rel "stylesheet" :href css-asset}]
     [:script {:src js-asset}]]))
