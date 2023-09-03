(ns vortext.esther.util.markdown
  (:require
   [clojure.string :as str]
   [table.core :as t]
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [vortext.esther.util.polyglot :as polyglot]))

(def parse
  (let [script "public/js/vendor/marked.min.js"
        script (str (fs/canonicalize (io/resource script)))
        marked (polyglot/js-api script "marked" [:parse])]
    (fn [& args]
      (apply (:parse marked) args))))

(defn strs-to-markdown-list [strs]
  (str/join "\n" (map #(str "- " (str/trim %)) strs)))

(defn table
  [obj]
  (t/table-str obj :style :github-markdown))
