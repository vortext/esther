(ns vortext.esther.config
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [kit.config :as config]))

(def ^:const system-filename "system.edn")

(defn system-config
  [options]
  (config/read-config system-filename options))

(def examples
  (edn/read-string
   (slurp (io/resource "prompts/examples.edn"))))

(def errors
  (edn/read-string
   (slurp (io/resource "prompts/errors.edn"))))

(def introductions
  (edn/read-string
   (slurp (io/resource "prompts/introductions.edn"))))

(def response-keys
  #{:reply :keywords
    :emoji :energy :image-prompt})

(def request-keys
  #{:msg :context})
