(ns vortext.esther.web.ui.common
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [jsonista.core :as json]
   [clojure.tools.logging :as log]
   [vortext.esther.util.crc32 :as crc32]))


(defn client-config
  [config]
  [:script {:type "text/javascript"}
   (str "window.clientConfig = "
        (json/write-value-as-string
          (or config {})) ";")])


(def default-styles
  ["public/css/fonts.css"
   "public/css/main.css"])


(def default-scripts
  ["public/js/vendor/htmx.js"])


(defn ->canonical-path
  [resource]
  (let [res (io/resource resource)]
    (str (fs/copy res (fs/create-temp-file) {:replace-existing true}))))

(defn minify-bin
  []
  (let [res (io/resource "scripts/minify/linux_amd64/minify")
        f (fs/copy res (fs/create-temp-file) {:replace-existing true})]
    (.setExecutable (fs/file f) true)
    (str f)))

(log/debug minify-bin)

(defn minify
  [paths outfile]
  (let [cmd [(minify-bin) "-b" "-o" outfile " " (str/join " " paths)]]
    (-> (shell {:out :string} (str/join " " cmd))
        deref :out) outfile))


(defn bundle
  [out-dir resources prefix suffix]
  (let [paths (map ->canonical-path resources)
        hash (crc32/checksum (apply str (map crc32/compute-file-crc32 paths)))
        filename (str prefix hash suffix)
        outfile (str (fs/path out-dir filename))]
    (if (fs/exists? outfile) outfile (minify paths outfile))))


(defn head
  [{:keys [config styles scripts]}]
  (let [out "public/assets"
        out-dir (fs/canonicalize (io/resource out))
        all-scripts (concat default-scripts (or scripts []))
        all-styles (concat default-styles (or styles []))
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
     (client-config config)
     [:link {:rel "stylesheet" :href css-asset}]
     [:script {:src js-asset}]]))
