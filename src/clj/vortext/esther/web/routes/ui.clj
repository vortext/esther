(ns vortext.esther.web.routes.ui
  (:require
   [clojure.tools.logging :as log]
   [ring.util.response :as response]
   [vortext.esther.web.middleware.exception :as exception]
   [vortext.esther.web.middleware.formats :as formats]
   [vortext.esther.web.middleware.auth :as auth]

   [vortext.esther.web.ui.conversation :as conversation]
   [vortext.esther.web.ui.signin :as signin]

   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [buddy.auth.accessrules :refer [wrap-access-rules]]
   [integrant.core :as ig]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]))


;; Routes
(defn ui-routes [opts]
  [["/" {:get
         (fn [req]
           (if (auth/authenticated? req)
             (response/redirect (:default-path opts))
             (signin/render opts req nil)))}]
   ["/signin"
    {:post (partial signin/handler opts)
     :get (fn [req] (signin/render opts req nil))}]
   ["/user/conversation"
    {:get (partial conversation/render opts)
     :post (partial conversation/message opts)}]])

(defn on-error
  [req _]
  (log/warn
   "access-rules on-error" " session:" (:session req))
  {:status 303
   :headers {"Location" "/signin"}
   :body "Redirecting to signin"})

(defn any-access [_] true)

(def access-rules [{:pattern #"/$"
                    :handler any-access}
                   {:pattern #"^/signin$"
                    :handler any-access}
                   {:pattern #"^/user/.*"
                    :handler auth/authenticated-access}])


(def route-data
  {:muuntaja   formats/instance
   :middleware
   [ ;; Default middleware for ui
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
