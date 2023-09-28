(ns vortext.esther.util.handlebars
  (:require
   [vortext.esther.util.json :as json]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [stringify-keys postwalk]])
  (:import [com.github.jknack.handlebars
            Handlebars$SafeString EscapingStrategy
            Handlebars Template Helper Options]))

(defonce ^Handlebars handlebars (Handlebars.))

(.with handlebars EscapingStrategy/NOOP) ;; ⚠

(defn safe-str
  [content]
  (Handlebars$SafeString. content))

;; Register the eq helper
(.registerHelper
 handlebars "eq"
 (proxy [Helper] []
   (apply [context ^Options options]
     (= (name context) (name (first (.params options)))))))

;; Register the json helper


(.registerHelper
 handlebars "json"
 (proxy [Helper] []
   (apply [context ^Options options]
     (json/write-value-as-string context))))


(defn compile-template
  [^String location]
  (.compile handlebars location))


(defn- compile-inline
  [^String template-str]
  (.compileInline handlebars template-str))


(defn- apply-template
  [^Template template ^Object obj]
  (.apply template obj))


(defn render-str
  [template-str obj]
  (-> (compile-inline template-str)
      (apply-template (stringify-keys obj))))

(defn render-template
  [location obj]
  (-> (compile-template location)
      (apply-template (stringify-keys obj))))

(defn mark-vals-as-safe
  [obj]
  (postwalk
   (fn [x]
     (if (map? x)
       (into {} (map (fn [[k v]]
                       [k (if (string? v)
                            (safe-str v)
                            v)])
                     x))
       x))
   obj))
