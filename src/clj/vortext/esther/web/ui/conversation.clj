(ns vortext.esther.web.ui.conversation
  (:require
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.controllers.memory :refer [todays-memories]]
   [vortext.esther.web.ui.common :as common]
   [vortext.esther.util :refer [random-base64 unescape-newlines]]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.util.time :as time]
   [clojure.string :as str]
   [markdown.core :as markdown]
   [clojure.tools.logging :as log]))


(def loading
  [:div.esther-typing-loading
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])

(defn display-html
  [s]
  (if (and (string? s) (not (str/blank? s)))
    (markdown/md-to-html-string (unescape-newlines s))
    "<span></span>"))

(defn memory-container
  [memory]
  (let [{:keys [request response]} memory
        {:keys [energy type]} response
        response-msg (:response response)
        type (or (keyword type) :default)
        msg (:msg request)]
    [:div.memory
     {"data-energy" energy}
     [:div.request
      (display-html msg)]
     [:div.response {:class (name type)}
      (if (and (string? response) (str/blank? response))
        [:span.md-sans "Silence."]
        (case type
          :htmx response-msg
          :md-mono (display-html response-msg)
          :md-sans (display-html response-msg)
          :md-serif (display-html response-msg)
          (display-html response-msg)))]]))

(defn message [opts request]
  (ui (memory-container (converse/answer! opts request))))


(defn msg-input [_request]
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
    [:input#user-context
     {:type "hidden"
      :name "context"
      :value "{}"}]
    [:input#user-sid.session-sid
     {:type "hidden"
      :name "sid"
      :value ""}]
    [:textarea#user-input
     {:autocomplete "off"
      :minlength 1
      :name "msg"
      :maxlength 1024
      :autofocus "true"
      :placeholder "Dear Esther,"
      :rows 2
      :oninput "resizeTextarea(event)"
      :onkeydown "handleTextareaInput(event);"}]]])

(defn conversation [opts request]
  (let [user (get-in request [:session :user])
        memories (reverse (todays-memories opts user))]
    [:div.container
     [:div#conversation.loading-state
      [:div#history
       (for [m memories]
         (memory-container m))]
      [:div#user-echo
       [:div#user-value {:class "user-message"}]]
      [:div#loading-response.loading-state loading]
      (msg-input request)]]))

(defn render [opts request]
  (let [sid (random-base64 10)]
    (page
     (common/head
      {:sid sid}
      [[:link {:rel "stylesheet" :href "/resources/public/css/conversation.css"}]]
      [[:script {:src "/resources/public/js/vendor/emoji.min.js"}]
       [:script {:src "/resources/public/js/vendor/suncalc.min.js"}]
       [:script {:src "/resources/public/js/vendor/marked.min.js"}]
       [:script {:src "/resources/public/js/conversation.js"}]])
     [:body
      [:div#container
       [:h1#title "Esther"]
       [:h2#subtitle (time/human-today) "."]
       (conversation opts request)]
      [:div#bottom]])))
