(ns vortext.esther.web.routes.ui
  (:require
    [buddy.auth.accessrules :refer [wrap-access-rules]]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [ring.util.response :as response]
    [vortext.esther.web.middleware.auth :as auth]
    [vortext.esther.web.middleware.exception :as exception]
    [vortext.esther.web.middleware.formats :as formats]
    [vortext.esther.web.ui.conversation :as conversation]
    [vortext.esther.web.ui.login :as login]
    [vortext.esther.web.ui.memory :as memory]))


;; Routes
(defn ui-routes
  [opts]
  [["/" {:get
         (fn [req]
           (if (auth/authenticated? req)
             (response/redirect (:default-path opts))
             (login/render opts req)))}]
   ["/login"
    {:post (partial login/handler opts)
     :get (fn [req] (login/render opts req))}]
   ["/logout"
    {:post
     (fn [_]
       {:status 301
        :session {:identity nil}
        :headers {"Location" "/"}})}]
   ["/user/conversation"
    {:get (partial conversation/render opts)
     :post (partial conversation/message opts)}]
   ["/user/archive"
    {:post (partial memory/archive opts)}]
   ["/user/wipe"
    {:post (partial memory/wipe opts)}]])


(defn on-error
  [req _]
  (log/warn
   "access-rules on-error" " session:" (:session req))
  {:status 301
   :headers {"Location" "/login"
             "HX-Refresh" "true"}
   :body "Redirecting to login"})


(defn any-access
  [_]
  true)


(def access-rules
  [{:pattern #"/$"
    :handler any-access}
   {:pattern #"^/login$"
    :handler any-access}
   {:pattern #"^/logout$"
    :handler auth/authenticated-access}
   {:pattern #"^/user/.*"
    :handler auth/authenticated-access}])


(def route-data
  {:muuntaja   formats/instance
   :middleware
   [;; Default middleware for ui
    ;; query-params & form-params
    parameters/parameters-middleware
    ;; encoding response body
    muuntaja/format-response-middleware
    ;; exception handling
    exception/wrap-exception
    ;; buddy security
    [wrap-access-rules {:rules access-rules
                        :on-error on-error}]
    [wrap-authorization auth/auth-backend]
    [wrap-authentication auth/auth-backend]]})


(derive :reitit.routes/ui :reitit/routes)


(defmethod ig/init-key :reitit.routes/ui
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (ui-routes opts)])
