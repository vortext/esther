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

(defn instant-to-local-date-time
  ([instant]
   (instant-to-local-date-time
    instant
    (java.time.ZoneId/systemDefault)))
  ([instant zone-id]
   (.toLocalDateTime (.atZone instant zone-id))))

(defn now [] (java.time.Instant/now))

(defn unix-ts [] (inst-ms (now)))

(defn millis-to-instant [millis]
  (java.time.Instant/ofEpochMilli millis))

(defn human-time-ago
  [epoch-milli]
  (h/datetime
   (instant-to-local-date-time (millis-to-instant epoch-milli))
   (millis-to-instant (unix-ts))))
