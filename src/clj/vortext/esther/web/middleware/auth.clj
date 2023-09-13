(ns vortext.esther.web.middleware.auth
  (:require
   [buddy.auth.backends :refer [session]]
   [buddy.auth.accessrules :refer [error]]
   [clojure.tools.logging :as log]
   [vortext.esther.web.controllers.users :as users]))

;; Create an instance of auth backend.
(def auth-backend (session))

(defn authenticate
  "Checks if request (with username/password :query-params)
  or username/password is valid"
  ([opts {:keys [params]}]
   (authenticate opts (:username params) (:password params)))
  ([opts username password]
   (users/retrieve opts username password)))

;; Access Level Handlers
(defn authenticated? [request]
  (some? (:identity (:session request))))


(defn authenticated-access
  "Check if request coming in is authenticated with user/password "
  [request]
  (if (authenticated? request) true (error "unauthenticated")))
