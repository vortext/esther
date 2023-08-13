(ns vortext.esther.secrets
  (:require
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [clojure.edn :as edn]))

(def secrets
  (memoize
   (fn []
     (edn/read-string
      (slurp
       (str
        (fs/expand-home (fs/path "~/.secrets.edn"))))))))
