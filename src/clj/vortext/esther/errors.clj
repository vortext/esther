(ns vortext.esther.errors
  (:require
    [clj-commons.format.exceptions :refer [format-exception *fonts*]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [kit.config :as config]))


(def errors
  (edn/read-string
    (slurp (io/resource "prompts/errors.edn"))))


(defn loggable-exception
  [e]
  (let [my-fonts
        {:exception     :bold.red
         :message       :italic
         :property      :bold
         :source        :green
         :app-frame     :bold.yellow
         :function-name :bold.yellow
         :clojure-frame :yellow
         :java-frame    :black
         :omitted-frame :faint.black}]
    (binding [*fonts* my-fonts]
      (str (.getMessage e) "\n" (format-exception e)))))


(defn wrapped-error
  [error-kw e]
  (log/error (loggable-exception e))
  {:event/role :system
   :event/conversation? true
   :event/content
   (-> (error-kw errors)
       (assoc :ui/type :error)
       (assoc :exception (str e)))})
