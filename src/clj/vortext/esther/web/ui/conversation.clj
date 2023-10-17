(ns vortext.esther.web.ui.conversation
  (:require
   [clj-commons.humanize :as h]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
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
  [{:keys [message]}]
  (if (and (string? message) (not (str/blank? message)))
    (markdown/parse message {"gfm" true "breaks" true})
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
        [:span.imagination
         {:style "display:none"} imagination])
      (case type
        :default [:pre.default (:message response)]
        :htmx (:message response)
        :error (error->html response)
        :md-mono (md->html response)
        :md-sans (md->html response)
        :md-serif (md->html response))]]))


(defn message
  [opts request]
  (let [response (converse/answer! opts request)]
    (-> (ui (memory-container response))
        (merge (meta response)))))


(defn msg-input
  [config placeholder]
  [:form.input-form
   {:id "message-form"
    :hx-post "/user/conversation"
    :hx-swap "beforeend settle:0.25s"
    :hx-boost "true"
    :hx-indicator ".loading-state"
    :hx-target "#history"
    :hx-trigger "submit"
    "hx-on::before-request" "beforeConverseRequest(event);"
    "hx-on::after-request" "afterConverseRequest(event);"}
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
     :maxlength converse/message-maxlength
     :autofocus "true"
     :placeholder (h/truncate
                    placeholder
                    (get config "maxPlaceholderLength"))
     :rows 3
     :oninput "resizeTextarea(event)"
     :onkeydown "handleTextareaInput(event);"}]])


(defn conversation
  [opts config request]
  (let [user (get-in request [:session :user])
        memories (memory/todays-non-archived-memories opts user)
        placeholder (or (memory/last-imagination opts user) "Dear Esther,")]
    [:section#conversation.loading-state
     [:article#history
      (for [m (reverse memories)]
        (memory-container m))]
     [:div#user-echo [:div#user-value]]
     [:div#loading-response.loading-state loading]
     [:span#placeholder {:style "display:none"} placeholder]
     (msg-input config placeholder)]))

(def assets
  (read-string (slurp (io/resource "public/assets/conversation.edn"))))

(defn render
  [opts request]
  (let [config {"maxPlaceholderLength" 300}]
    (page (common/head (assoc assets :config config))
          [:main#container
           [:h1#title "Esther"]
           [:h2#today]
           (conversation opts config request)
           [:div#bottom]])))
