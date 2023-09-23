(ns vortext.esther.util.handlebars
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.walk :refer [stringify-keys]]
   [clojure.string :as str])
  (:import [com.github.jknack.handlebars Handlebars Template]))

(defonce ^Handlebars handlebars (Handlebars.))

(defn- ^Template compile-inline
  [^String template-str]
  (.compileInline handlebars template-str))


(defn- apply-template
  [^Template template ^Object obj]
  (.apply template obj))


(defn render
  [template-str obj]
  (-> (compile-inline template-str)
      (apply-template (stringify-keys obj))))
