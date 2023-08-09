(ns vortext.esther.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[esther starting]=-"))
   :start      (fn []
                 (log/info "\n-=[esther started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[esther has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
