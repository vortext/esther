(ns vortext.esther.web.ui.memory
  (:require
    [clj-commons.humanize :as h]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [vortext.esther.util.markdown :as markdown]
    [vortext.esther.web.controllers.memory :as memory]
    [vortext.esther.web.htmx :refer [ui] :as htmx]))


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
    (markdown/table
      (map #(select-keys % ks) formatted-keywords))))


(defn md-memories-table
  [memories]
  (let [ks [:emoji :keywords :imagination]
        responses (map #(-> % :memory/events second) memories)
        update-kw
        (fn [kw] (str/join ", " kw))
        formatted-responses
        (map (fn [event]
               (-> (:event/content event)
                   (update :keywords update-kw)))
             responses)]
    (markdown/table
      (map #(select-keys % ks) formatted-responses))))


(defn forget-form
  [_opts _user scope]
  (let [scope (if (and (string? scope)
                       (not (str/blank? scope)))
                (keyword (str/trim scope))
                :session)
        allowed #{:today :all}]
    (if (not (allowed scope))
      [:span "The only allowed options are " (h/oxford (map name allowed)) "."]
      [:form.confirmation
       {:hx-post "/user/forget"
        :hx-swap "outerHTML"}
       [:div
        {:style "padding-bottom: 1em"}
        [:strong (str "Are you sure you want to forget " (name scope) " memory?")]]
       [:button.button.button-primary
        {:name "action" :value "forget"} "Forget memory"]
       [:button.button.button-info
        {:name "action" :value "cancel"} "Cancel"]
       [:input {:type :hidden :name "scope" :value scope}]])))


(defn forget
  [opts {:keys [params] :as request}]
  (let [action (keyword (:action params))
        user (get-in request [:session :user])
        {:keys [uid]} (:vault user)
        scope (keyword (:scope params))
        scopes {:today memory/forget-today!
                :all memory/forget-all!}]
    (if (= action :forget)
      (-> (ui (do ((scopes scope) opts user)
                  [:span "Forgot memories: " (name scope)]))
          (assoc :headers {"HX-Redirect" "/"}))
      (-> (ui [:span "Let us continue."])
          (update :headers merge {"HX-Trigger" "enableUserInput"})))))


(defn archive-form
  [_opts _user]
  [:form.confirmation
   {:hx-post "/user/archive"
    :hx-swap "outerHTML"}
   [:div
    {:style "padding-bottom: 1em"}
    [:strong (str "Are you sure you want to archive this conversation?")]]
   [:button.button.button-primary
    {:name "action" :value "archive"} "Archive conversation"]
   [:button.button.button-info
    {:name "action" :value "cancel"} "Cancel"]])


(defn archive
  [opts {:keys [params] :as request}]
  (let [action (keyword (:action params))
        user (get-in request [:session :user])]
    (if (= action :archive)
      (-> (ui (do (memory/archive-todays-memories opts user)
                  [:span "Archived conversation."]))
          (assoc :headers {"HX-Redirect" "/"}))
      (-> (ui [:span "Let us continue."])
          (update :headers merge {"HX-Trigger" "enableUserInput"})))))
