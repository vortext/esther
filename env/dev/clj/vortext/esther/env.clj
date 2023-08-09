(ns vortext.esther.env
  (:require
    [clojure.tools.logging :as log]
    [vortext.esther.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[esther starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[esther started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[esther has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev
                :persist-data? true}})
