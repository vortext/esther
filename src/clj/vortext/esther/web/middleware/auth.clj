(ns vortext.esther.web.middleware.auth
  (:require
   [clojure.tools.logging :as log]
   [buddy.hashers :as hashers]
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer [bytes->hex]]
   [buddy.auth.backends :refer [session]]
   [buddy.auth.accessrules :refer [success error]]
   [buddy.auth :refer [authenticated?]]))


;; Create an instance of auth backend.
(def auth-backend (session))

(defn insert-user!
  [{:keys [db]} username email password]
  (let [uid (-> (hash/sha256 username) (bytes->hex))]
    (when (nil? ((:query-fn db)
                 :find-user-by-username {:username username}))
      ((:query-fn db)
       :create-user
       {:uid uid
        :username username
        :email email
        :password_hash (hashers/encrypt password)}))))

(defn authenticate
  "Checks if request (with username/password :query-params)
  or username/password is valid"
  ([db request]
   (let [username (get-in request [:params :username])
         password (get-in request [:params :password])]
     (authenticate db username password)))
  ([db username password]
   (if (and username password)
     (when-let [user ((:query-fn db) :find-user-by-username {:username username})]
       (when (hashers/check password (:password_hash user))
         (dissoc user :password_hash)))
     nil)))

;; authentication handler used with buddy ring wrappers

(defn auth-handler
  [opts request]
  (log/info "auth handler" (:params request))
  (if (authenticate (:db opts) request)
    (get-in request [:params :username])
    nil))


;; Access Level Handlers

(defn authenticated-access
  "Check if request coming in is authenticated with user/password
  or a valid JWT token"
  [opts request]
  (if (or (authenticated? request)
          ((partial authenticate (:db opts)) request))
    true
    (error "access not allowed")))
