(ns vortext.esther.web.controllers.health
  (:require
   [vortext.esther.web.routes.utils :as utils]
   [clojure.tools.logging :as log]
   [ring.util.http-response :as http-response])
  (:import
   [java.util Date]))

(defn healthcheck!
  [opts req]
  (let [_ (log/info opts)
        query-fn (:query-fn opts)
        ])
  (http-response/ok
   {:time     (str (Date. (System/currentTimeMillis)))
    :up-since (str (Date. (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean))))
    :app      {:status  "up"
               :message ""}}))
