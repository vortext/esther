(ns vortext.esther.web.ui.memory
  (:require
   [clojure.tools.logging :as log]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [clj-commons.humanize :as h]
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
  (let [ks [:emoji :energy :keywords :image-prompt]
        csv-keywords #(assoc % :keywords (str/join ", " (:keywords %)))]
    (t/table-str
     (map #(select-keys % ks) (map (comp csv-keywords :response) memories))
     :style :github-markdown)))

(defn clear-form
  [_opts _user sid scope]
  (let [scope (if (and (string? scope)
                       (not (str/blank? scope)))
                (keyword (str/trim scope))
                :session)
        allowed #{:session :today :all}]
    (if (not (allowed scope))
      [:span "The only allowed options are " (h/oxford (map name allowed)) "."]
      [:form.confirmation
       {:hx-post "/user/clear"
        :hx-swap "outerHTML"}
       [:div
        {:style "padding-bottom: 1em"}
        [:strong (str "Are you sure you want to clear " (name scope) " memory?")]]
       [:button.button.button-primary
        {:name "action" :value "clear"} "Clear memory"]
       [:button.button.button-info
        {:name "action" :value "cancel"} "Cancel"]
       [:input {:type :hidden :name "sid" :value sid}]
       [:input {:type :hidden :name "scope" :value scope}]])))

(defn clear
  [opts {:keys [params] :as request}]
  (let [action (keyword (:action params))
        user (get-in request [:session :user])
        scope (keyword (:scope params))
        scopes {:today memory/clear-today!
                :session memory/clear-session!
                :all memory/clear-all!}]
    (if (= action :clear)
      (-> (ui (do ((scopes scope) opts user (:sid params))
                  [:span "Wiped memories: " (name scope)]))
          (assoc :headers {"HX-Redirect" "/"}))
      (ui [:span "Let us continue."]))))
