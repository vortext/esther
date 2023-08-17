(ns vortext.esther.web.ui.memory
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.web.controllers.memory :as memory]
   [clojure.string :as str]
   [table.core :as t]))

(defn md-keywords-table
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

(defn md-memories-table
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

(defn clear-form
  [_opts _user]
  [:form.confirmation
   {:hx-post "/user/clear"
    :hx-swap "outerHTML"}
   [:button.button.button-primary
    {:name "action" :value "clear"} "Clear memory"]
   [:button.button.button-info
    {:name "action" :value "cancel"} "Cancel"]])

(defn clear
  [opts {:keys [params] :as request}]
  (let [action (keyword (:action params))
        user (get-in request [:session :user])]
    (ui (if (= action :clear)
          (do (memory/clear! opts user)
              [:span "Wiped all memories of you."])
          [:span "Let us continue."]))))
