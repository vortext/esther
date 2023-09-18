(ns vortext.esther.config
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [kit.config :as config]))


(def ^:const app-name "esther")

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
    :emoji :imagination})


(def request-keys
  #{:content :context})


(def cache-dir
  (fs/create-dirs (fs/xdg-cache-home app-name)))
