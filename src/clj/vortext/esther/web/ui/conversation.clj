(ns vortext.esther.web.ui.conversation
  (:require
   [vortext.esther.web.controllers.converse :as converse]
   [vortext.esther.web.controllers.memory :as memory]
   [vortext.esther.web.ui.common :as common]
   [vortext.esther.web.htmx :refer [page ui] :as htmx]
   [vortext.esther.util.time :as time]
   [vortext.esther.util.markdown :as markdown]
   [clojure.string :as str]))

(def loading
  [:div.esther-typing-loading
   [:div.loading
    [:div.first]
    [:div.second]
    [:div.third]]])


(defn display-html
  [s]
  (if (and (string? s) (not (str/blank? s)))
    (markdown/parse s {"gfm" true "breaks" true})
    "<span></span>"))

(defn memory-container
  [memory]
  (let [{:keys [request response]} memory
        {:keys [energy type reply]} response
        type (or (keyword type) :default)
        request-msg (:msg request)]
    [:div.memory
     {"data-energy" energy}
     [:div.request
      (display-html request-msg)]
     [:div.response {:class (name type)}
      (if (and (string? response) (str/blank? response))
        [:span.md-sans "Silence."]
        (case type
          :htmx reply
          :ui reply
          :md-mono (display-html reply)
          :md-sans (display-html reply)
          :md-serif (display-html reply)
          (display-html reply)))]]))

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
    [:textarea#user-input
     {:autocomplete "off"
      :minlength 1
      :name "msg"
      :maxlength 1024
      :autofocus "true"
      :placeholder "Dear Esther,"
      :rows 12
      :oninput "resizeTextarea(event)"
      :onkeydown "handleTextareaInput(event);"}]]])

(defn conversation [opts request]
  (let [user (get-in request [:session :user])
        memories (memory/todays-non-archived-memories opts user)]
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
  (page
   (common/head
    {;; London as default
     ;; [TODO] Don't rely on ip addr for weather info
     :latitude 51.509865
     :longitude -0.118092}
    [[:link {:rel "stylesheet" :href "/resources/public/css/conversation.css"}]]
    [[:script {:src "/resources/public/js/vendor/emoji.min.js"}]
     [:script {:src "/resources/public/js/vendor/suncalc.min.js"}]
     [:script {:src "/resources/public/js/vendor/marked.min.js"}]
     [:script {:src "/resources/public/js/vendor/lunarphase.js"}]
     [:script {:src "/resources/public/js/conversation.js"}]])
   [:body
    [:div#container
     [:h1#title "Esther"]
     [:h2#subtitle (time/human-today) "."]
     (conversation opts request)]
    [:div#bottom]]))
