(ns vortext.esther.errors
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
   [kit.config :as config]))

(def errors
  (edn/read-string
   (slurp (io/resource "prompts/errors.edn"))))

(defn wrapped-error
  [error-kw e]
  (log/warn e)
  {:event/role :system
   :event/content
   (-> (error-kw errors)
       (assoc :ui/type :error)
       (assoc :exception (str e)))})
