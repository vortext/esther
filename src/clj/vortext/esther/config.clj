(ns vortext.esther.config
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
   [kit.config :as config]))

(def ai-name "esther")

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (config/read-config system-filename options))

(def examples
  (edn/read-string
   (slurp (io/resource "prompts/examples.edn"))))

(def introductions
  (edn/read-string
   (slurp (io/resource "prompts/introductions.edn"))))

(def response-keys
  #{:content :keywords
    :emoji :energy :imagination})

(def request-keys
  #{:content :context})
