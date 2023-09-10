(ns vortext.esther.util.time
  (:require
   [clj-commons.humanize :as h]
   [java-time.api :as jt])
  (:import [java.time.format TextStyle]))

(def default-locale (java.util.Locale/getDefault))
(def default-zone-id (java.time.ZoneId/of "UTC"))

(defn day-name
  [day locale]
  (.getDisplayName day TextStyle/FULL_STANDALONE locale))

(defn month-name
  [month locale]
  (.getDisplayName month TextStyle/FULL_STANDALONE locale))

(defn human-today
  ([] (human-today default-zone-id default-locale))
  ([zone-id locale]
   (let [present (jt/local-date (jt/instant) zone-id)
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
   (instant-to-local-date-time instant default-zone-id))
  ([instant zone-id]
   (.toLocalDateTime (.atZone instant zone-id))))

(defn now
  ([] (now default-zone-id))
  ([zone-id] (jt/instant zone-id)))

(defn unix-ts [] (inst-ms (now)))

(defn human-time-ago
  ([epoch-milli]
   (human-time-ago (jt/instant epoch-milli) (now)))
  ([inst1 inst2]
   (h/datetime
    (instant-to-local-date-time inst1)
    (instant-to-local-date-time inst2))))
