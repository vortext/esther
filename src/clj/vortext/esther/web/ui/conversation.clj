(ns vortext.esther.web.ui.conversation
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [vortext.esther.util.markdown :as markdown]
    [vortext.esther.util.time :as time]
    [vortext.esther.web.controllers.converse :as converse]
    [vortext.esther.web.controllers.memory :as memory]
    [vortext.esther.web.htmx :refer [page ui] :as htmx]
    [vortext.esther.web.ui.common :as common]))


(def loading
  [:div.esther-typing-loading
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
     {"data-energy" (:energy response)}
     [:div.request (md->html request)]
     [:div.response {:class (name type)}
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
  [_request]
  [:div.input-form
   [:form
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
      :placeholder "Dear Esther,"
      :rows 12
      :oninput "resizeTextarea(event)"
      :onkeydown "handleTextareaInput(event);"}]]])


(defn conversation
  [opts request]
  (let [user (get-in request [:session :user])
        memories (reverse (memory/todays-non-archived-memories opts user))]
    [:div.container
     [:div#conversation.loading-state
      [:div#history
       (for [m memories]
         (memory-container m))]
      [:div#user-echo
       [:div#user-value {:class "user-message"}]]
      [:div#loading-response.loading-state loading]
      (msg-input request)]]))


(defn render
  [opts request]
  (page
    (common/head
      {} ; inject appConfig here
      ["public/css/conversation.css"]
      ["public/js/vendor/emoji.js"
       "public/js/vendor/marked.js"
       "public/js/conversation.js"])
    [:body
     [:div#container
      [:h1#title "Esther"]
      [:h2#today]
      (conversation opts request)]
     [:div#bottom]]))
