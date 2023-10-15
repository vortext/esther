(ns vortext.esther.web.ui.memory
  (:require
   [clj-commons.humanize.inflect :as i]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [vortext.esther.common :refer [parse-number]]
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
  (let [scope (or (parse-number scope) scope)]
    (if (memory/allowed-to-forget? scope)
      (with-meta
        [:form.confirmation
         {:hx-post "/user/forget"
          :hx-swap "outerHTML"}
         [:div.nudge-bottom
          [:strong
           (if (= scope "")
             "Are you sure you want to forget the last memory?"
             (format "Are you sure you want to forget %s %s?"
                     scope
                     (i/pluralize-noun (if (number? scope) scope 2) "memory")))]]
         [:button.button.button-primary
          {:name "action" :value "forget"} "Forget"]
         [:button.button.button-info
          {:name "action" :value "cancel"} "Cancel"]
         [:input {:type :hidden :name "scope" :value scope}]]
        {:headers {"HX-Trigger" "disableUserInput"}})
      (markdown/parse
       "Allowed options for forgetting are: `all`, `today`, or a number `n` for the `last n` memories."))))

(defn forget
  [opts {:keys [params] :as request}]
  (let [action (keyword (:action params))
        user (get-in request [:session :user])
        scope (:scope params)
        scope (or (parse-number scope) scope)]
    (if (= action :forget)
      (-> (ui (do (memory/forget! opts user scope)
                  [:span (format "Forgot %s memories" scope)]))
          (assoc :headers {"HX-Redirect" "/"}))
      ;; Cancel
      (-> (ui [:span "Let us continue."])
          (update :headers merge {"HX-Trigger" "enableUserInput"})))))


(defn archive-form
  [_opts _user]
  [:form.confirmation
   {:hx-post "/user/archive"
    :hx-swap "outerHTML"}
   [:div.nudge-bottom
    [:strong (str "Are you sure you want to archive this conversation?")]]
   [:button.button.button-primary
    {:name "action" :value "archive"} "Archive"]
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
      ;; Cancel
      (-> (ui [:span "Let us continue."])
          (update :headers merge {"HX-Trigger" "enableUserInput"})))))
