(ns vortext.esther.config
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [kit.config :as config]))

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (config/read-config system-filename options))

(defn secrets []
  (get-in (system-config {}) [:handler/ring :secrets]))

(def examples
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/examples.edn"))))

(def errors
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/errors.edn"))))

(def introductions
  (edn/read-string
   (slurp (io/resource "prompts/scenarios/introductions.edn"))))
