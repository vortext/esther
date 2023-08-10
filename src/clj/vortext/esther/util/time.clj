(ns vortext.esther.util.time
  (:require
   [clj-commons.humanize :as h]
   [java-time.api :as jt])
  (:import [java.time.format TextStyle]))

(def default-locale (java.util.Locale/getDefault))

(defn day-name
  [day locale]
  (.getDisplayName day TextStyle/FULL_STANDALONE locale))

(defn month-name
  [month locale]
  (.getDisplayName month TextStyle/FULL_STANDALONE locale))

(defn human-today
  ([] (human-today default-locale))
  ([locale]
   (let [present (jt/local-date)
         day (jt/day-of-week present)
         day-month (.getDayOfMonth (jt/month-day present))
         month (jt/month present)
         year (jt/year present)]
     (str (day-name day locale)
          " the "
          (h/ordinal day-month)
          " of " (month-name month locale) ", "
          year))))
