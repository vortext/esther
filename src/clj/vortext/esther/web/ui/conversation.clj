(ns vortext.esther.web.ui.conversation
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [vortext.esther.util.markdown :as markdown]
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.web.ui.common :as common]))


(def loading
  [:div.response-loading
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])


(defn md->html
  [{:keys [content]}]
  (if (and (string? content) (not (str/blank? content)))
    (markdown/parse content {"gfm" true "breaks" true})
    "<span></span>"))


(defn error->html
  [{:keys [exception] :as event}]
  [:span.error
   (md->html event)
   [:span.exception exception]])


(defn memory-container
  [{:keys [:memory/events]}]
  (let [[req rep] events
        [request response] [(:event/content req)
                            (:event/content rep)]
        type (keyword (get response :ui/type :default))]
    [:div.memory
     [:div.request (md->html request)]
     [:div.response
      {:class (name type)}
      (when-let [imagination (:imagination response)]
        [:span.imagination imagination])
      (case type
        :default [:pre.default (:content response)]
        :htmx (:content response)
        :ui (:content response)
        :error (error->html response)
        :md-mono (md->html response)
        :md-sans (md->html response)
        :md-serif (md->html response))]]))


(defn message
  [opts request]
  (ui (memory-container (converse/answer! opts request))))


(defn msg-input
  [placeholder]
  [:form.input-form
   {:id "message-form"
    :hx-post "/user/conversation"
    :hx-swap "beforeend settle:0.25s"
    :hx-boost "true"
    :hx-indicator ".loading-state"
    :hx-target "#history"
    :hx-trigger "submit"
    "hx-on::before-request" "beforeConverseRequest();"
    "hx-on::after-request" "afterConverseRequest();"}
   [:input#client-context
    {:type "hidden"
     :name "client-context"
     :value "{}"}]
   [:input#input-content
    {:type "hidden"
     :name "content"
     :value ""}]
   [:textarea#user-input
    {:autocomplete "off"
     :minlength 1
     :name "_content"
     :maxlength 1024
     :autofocus "true"
     :placeholder placeholder
     :rows 2
     :oninput "resizeTextarea(event)"
     :onkeydown "handleTextareaInput(event);"}]])


(defn conversation
  [opts request]
  (let [user (get-in request [:session :user])
        memories (memory/todays-non-archived-memories opts user)
        placeholder (memory/last-imagination opts user)]
    [:div.container
     [:div#conversation.loading-state
      [:div#history
       (for [m (reverse memories)]
         (memory-container m))]
      [:div#user-echo
       [:div#user-value {:class "user-message"}]]
      [:div#loading-response.loading-state loading]
      (msg-input placeholder)]]))


(defn render
  [{:keys [ai] :as opts} request]
  (page
   (common/head
    {:config {}
     :styles ["public/css/conversation.css"]
     :scripts ["public/js/vendor/emoji.js"
               "public/js/vendor/marked.js"
               "public/js/vendor/sentiment.js"
               "public/js/conversation.js"]})
   [:main
    [:div#container
     [:h1#title (:name ai)]
     [:h2#today]
     (conversation opts request)]
    [:div#bottom]]))
