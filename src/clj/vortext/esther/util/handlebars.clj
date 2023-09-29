(ns vortext.esther.util.handlebars
  (:require
   [vortext.esther.util.json :as json]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [clojure.walk :refer [stringify-keys]])
  (:import [com.github.jknack.handlebars
            Handlebars$SafeString EscapingStrategy
            Handlebars Template Helper Options]))


(defn create-instance
  []
  (doto (Handlebars.)
    (.with EscapingStrategy/NOOP) ;; ⚠
    (.registerHelper
     "eq"
     (proxy [Helper] []
       (apply [context ^Options options]
         (= (name context) (name (first (.params options)))))))

    (.registerHelper
     "json"
     (proxy [Helper] []
       (apply [context ^Options options]
         (json/write-value-as-string context))))))


(defn apply-template
  [^Template template ^Object obj]
  (.apply template obj))


(defn render-str
  [^Handlebars handlebars ^String template-str obj]
  (-> (.compileInline handlebars template-str)
      (apply-template (stringify-keys obj))))


(defn render-template
  [^Handlebars handlebars ^String location obj]
  (->
   (.compile handlebars location)
   (apply-template (stringify-keys obj))))


(defn register-helper
  [handlebars name f]
  (.registerHelper
   handlebars
   name
   (proxy [Helper] []
     (apply [context ^Options options]
       (f context options)))))


(defmethod ig/init-key :util.handlebars/instance
  [_ _options]
  (let [instance (create-instance)]
    {:handlebars/instance instance
     :handlebars/register-helper (partial register-helper instance)
     :handlebars/render-str (partial render-str instance)
     :handlebars/render-template (partial render-template instance)}))


(defmethod ig/halt-key! :util.handlebars [_ opts] nil)
