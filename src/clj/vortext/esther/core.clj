(ns vortext.esther.core
  (:gen-class)
  (:require
    [clojure.tools.logging :as log]
    [integrant.core :as ig]
    [kit.edge.db.sql.conman]
    [kit.edge.db.sql.migratus]
    ;; Edges
    [kit.edge.server.undertow]
    [kit.edge.utils.nrepl]
    [vortext.esther.ai.grammar]
    [vortext.esther.ai.llama]
    ;; AI
    [vortext.esther.ai.llm]
    [vortext.esther.config :as config]
    [vortext.esther.env :refer [defaults]]
    [vortext.esther.errors :as errors]
    ;; Util
    [vortext.esther.util.handlebars]
    [vortext.esther.web.handler]
    ;; Routes
    [vortext.esther.web.routes.api]
    [vortext.esther.web.routes.ui]))


;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException
      [_ thread ex]
      (log/error (errors/loggable-exception ex)))))


(defonce system (atom nil))


(defn stop-app
  []
  ((or (:stop defaults) (fn [])))
  (some-> (deref system) (ig/halt!))
  (shutdown-agents))


(defn start-app
  [& [params]]
  ((or (:start params) (:start defaults) (fn [])))
  (->> (config/system-config (or (:opts params) (:opts defaults) {}))
       (ig/prep)
       (ig/init)
       (reset! system))
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))


(defn -main
  [& _]
  (log/info "starting...")
  (alter-var-root  #'clj-commons.ansi/*color-enabled* (constantly false))
  (start-app))
