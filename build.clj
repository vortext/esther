(ns build
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def lib 'vortext/esther)
(def main-cls (str/join "." (filter some? [(namespace lib) (name lib) "core"])))
(def version (format "0.0.1-SNAPSHOT"))
(def target-dir "target")
(def class-dir (str target-dir "/" "classes"))
(def uber-file (format "%s/%s-standalone.jar" target-dir (name lib)))
(def basis (b/create-basis {:project "deps.edn"}))
(def public-path "./resources/public/")

(defn- minify-
  [paths outfile]
  (try
    (let [bin (fs/canonicalize "scripts/minify_linux_amd64/minify")
          cmd [bin "-b" "-o" outfile " " (str/join " " paths)]]
      (-> (shell {:out :string} (str/join " " cmd))
          deref :out) outfile)
    (catch Exception _ (println (str "Could not minify" outfile)))))

(defn bundle
  [{:keys [files bundle]}]
  (let []
    (minify-
     (map (comp fs/canonicalize #(str public-path %)) files)
     (fs/canonicalize (str public-path bundle)))))


(defn minify-assets
  "Minifies the front-end assets"
  [_]
  (let [assets (fs/glob "resources/public/assets/" "*.edn")
        assets (mapcat (comp vals read-string slurp str) assets)]
    (println "Minifying front-end assets...")
    (doseq [asset assets]
      (println (str "Wrote: " (str (bundle asset))))))
  (println "Done minifying assets."))


(defn clean
  "Delete the build target directory"
  [_]
  (println (str "Cleaning " target-dir))
  (b/delete {:path target-dir})
  (println "Cleaning minified assets")
  (doall (map fs/delete (fs/glob (fs/path public-path "assets") "*.min.*"))))


(defn prep [_]
  (println "Writing Pom...")
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src/clj"]})
  (minify-assets nil)
  (println "Copying to target-dir...")
  (b/copy-dir {:src-dirs ["src/clj" "resources" "env/prod/resources" "env/prod/clj"]
               :target-dir class-dir}))

(defn uber [_]
  (println "Compiling Clojure...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src/clj/vortext/esther/web"
                             "src/clj/vortext/esther/util"
                             ;; Do not add AI folder as clong does
                             ;; funky import-structs! dynamic class building
                             "env/prod/resources" "env/prod/clj"]
                  :class-dir class-dir})
  (println "Making uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :main main-cls
           :basis basis}))

(defn all [_]
  (do (clean nil) (prep nil) (uber nil)))
