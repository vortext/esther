(ns vortext.esther.web.ui.conversation
  (:require
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.ui.common :as common]
   [vortext.esther.util :refer [random-base64]]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.util.time :as time]
   [markdown.core :as markdown]
   [clojure.tools.logging :as log]))


(def loading
  [:div.esther-typing-loading
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])

(defn message [opts request]
  (let [response (:response (converse/answer! opts request))
        {:keys [energy type response]} response
        type (or type :default)
        md #(markdown/md-to-html-string %)
        result
        (case type
          :htmx response
          :md-mono (md response)
          :md-sans (md response)
          :md-serif (md response)
          :else (md response))

        md-request (md (get-in request [:params :msg]))]
    (ui
     [:div.memory
      {"data-energy" energy}
      [:div.request md-request]
      [:div.response {:class (name type)} result]])))


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
    [:input.session-sid
     {:type "hidden"
      :name "sid"
      :value ""}]
    [:textarea#user-input
     {:autocomplete "off"
      :minlength 1
      :maxlength 1024
      :autofocus "true"
      :placeholder "Dear Esther,"
      :name "msg"
      :rows 1
      :onkeydown "handleTextareaInput(event);"}]]])

(defn conversation [_opts request]
  [:div.container
   [:div#conversation.loading-state
    [:div#history]
    [:div#user-echo
     [:div#user-value {:class "user-message"}]]
    [:div#loading-response.loading-state loading]
    (msg-input request)]])

(defn render [opts request]
  (let [sid (random-base64 10)]
    (page
     (common/head
      {:sid sid}
      [[:link {:rel "stylesheet" :href "/resources/public/css/conversation.css"}]]
      [[:script {:src "/resources/public/js/vendor/suncalc.min.js"}]
       [:script {:src "/resources/public/js/conversation.js"}]])
     [:body
      [:div#container
       [:h1#title "Esther"]
       [:h2#subtitle (time/human-today) "."]
       (conversation opts request)]])))
