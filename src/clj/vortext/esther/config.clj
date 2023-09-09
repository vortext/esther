(ns vortext.esther.config
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
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

(defn wrapped-response
  [type content]
  {:ui/type type
   :converse/response {:content content}})

(defn wrapped-error
  [error-kw e]
  (log/warn e)
  {:ui/type :error
   :converse/response
   (-> (error-kw errors)
       (assoc :exception (str e)))})

(def introductions
  (edn/read-string
   (slurp (io/resource "prompts/introductions.edn"))))

(def response-keys
  #{:content :keywords
    :emoji :energy :imagination})

(def request-keys
  #{:content :context})
