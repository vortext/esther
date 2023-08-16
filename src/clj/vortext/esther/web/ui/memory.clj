(ns vortext.esther.web.ui.memory
  (:require
   [clojure.string :as str]
   [table.core :as t]
   [clojure.tools.logging :as log]))

(defn keywords-table
  [keywords]
  (let [ks [:value :frecency :frequency :recency]
        formatted-keywords
        (map (fn [kw]
               (-> kw
                   (update :frecency #(format "%.2f" %))
                   (update :frequency #(format "%d" %))
                   (update :recency #(format "%.2f" %))))
             keywords)]
    (t/table-str
     (map #(select-keys % ks) formatted-keywords)
     :style :github-markdown)))

(defn memories-table
  [memories]
  (let [responses (map
                   (fn [memory]
                     (let [response (:response memory)
                           kw (:keywords response)]
                       (assoc
                        response :keywords
                        (str/join ", " kw))))
                   memories)
        ks [:emoji :energy :keywords :image-prompt]]
    (t/table-str
     (map #(select-keys % ks) responses)
     :style :github-markdown)))
