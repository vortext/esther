(ns vortext.esther.core
  (:require
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [vortext.esther.config :as config]
   [vortext.esther.env :refer [defaults]]
   [vortext.esther.web.middleware.auth :refer [insert-user!]]

   ;; Edges
   [kit.edge.server.undertow]
   [vortext.esther.web.handler]

   ;; Routes
   [vortext.esther.web.routes.api]
   [vortext.esther.web.routes.ui]
   [kit.edge.utils.nrepl]
   [kit.edge.db.sql.conman]
   [kit.edge.db.sql.migratus])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what :uncaught-exception
                  :exception ex
                  :where (str "Uncaught exception on" (.getName thread))}))))

(defonce system (atom nil))

;; Database

(defmethod ig/init-key :dev/init-test-user
  [_ {:keys [db] :as opts}]
  (let [username "test"
        password "test"]
    (when (nil? ((:query-fn db) :find-user-by-username {:username username}))
      (do
        (log/warn ":db.sql/init creating user " username " with password " password)
        (insert-user! opts username password)))))

(defn stop-app []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!))
  (shutdown-agents))

(defn start-app [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts defaults) {}))
       (ig/prep)
       (ig/init)
       (reset! system))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& _]
  (start-app))
