(ns vortext.esther.config
  (:require
    [kit.config :as config]))

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (config/read-config system-filename options))

(defn secrets []
  (get-in (system-config {}) [:handler/ring :secrets]))
