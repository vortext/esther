(ns vortext.esther.web.middleware.auth
  (:require
   [clojure.tools.logging :as log]
   [buddy.hashers :as hashers]
   [buddy.core.hash :as hash]
   [buddy.core.codecs :refer [bytes->hex]]
   [buddy.auth.backends :refer [session]]
   [ring.util.response :as response]
   [buddy.auth.accessrules :refer [success error]]))

;; Create an instance of auth backend.
(def auth-backend (session))

(defn insert-user!
  [{:keys [db]} username password]
  (let [uid (-> (hash/sha256 username) (bytes->hex))]
    (when (nil? ((:query-fn db)
                 :find-user-by-username {:username username}))
      ((:query-fn db)
       :create-user
       {:uid uid
        :username username
        :password_hash (hashers/encrypt password)}))))

(defn authenticate
  "Checks if request (with username/password :query-params)
  or username/password is valid"
  ([opts request]
   (let [username (get-in request [:params :username])
         password (get-in request [:params :password])]
     (authenticate opts username password)))
  ([opts username password]
   (let [query-fn (get-in opts [:db :query-fn])
         user (query-fn :find-user-by-username {:username username})]
     (log/info "authenticate:user" user)
     (if (and username password (hashers/check password (:password_hash user)))
       (:uid user) nil))))


;; Access Level Handlers
(defn authenticated? [request]
  (some? (:identity (:session request))))


(defn authenticated-access
  "Check if request coming in is authenticated with user/password "
  [request]
  (log/info "authenticated-access:session" (:session request))
  (let [valid? (authenticated? request)]
    (if valid? true (error "unauthenticated"))))
