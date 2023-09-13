(ns vortext.esther.util.time
  (:require
    [babashka.fs :as fs]
    [clj-commons.humanize :as h]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [java-time.api :as jt]
    [vortext.esther.util.polyglot :as polyglot])
  (:import
    (java.time.format
      TextStyle)))


(def default-locale (java.util.Locale/getDefault))
(def default-zone-id (java.time.ZoneId/of "UTC"))

(def ->local-date jt/local-date) ; Alias
(def ->local-date-time jt/local-date-time)


(defn ->iso8601
  [local-date-time]
  (jt/format java.time.format.DateTimeFormatter/ISO_DATE_TIME local-date-time))


(defn iso8601->offset-date-time
  [iso8601]
  (java.time.OffsetDateTime/parse iso8601))


(defn day-name
  [day locale]
  (.getDisplayName day TextStyle/FULL_STANDALONE locale))


(defn month-name
  [month locale]
  (.getDisplayName month TextStyle/FULL_STANDALONE locale))


(defn human-today
  ([] (human-today
       (->local-date (jt/instant) default-zone-id) default-locale))
  ([present locale]
   (let [day (jt/day-of-week present)
         day-month (.getDayOfMonth (jt/month-day present))
         month (jt/month present)
         year (jt/year present)]
     (str (day-name day locale)
          " the "
          (h/ordinal day-month)
          " of " (month-name month locale) ", "
          year))))


(defn season
  [date latitude]
  (let [local-date (jt/local-date date)
        year (jt/year local-date)
        march-equinox (jt/local-date year 3 21)
        june-solstice (jt/local-date year 6 21)
        september-equinox (jt/local-date year 9 23)
        december-solstice (jt/local-date year 12 21)]
    (if (> latitude 0)
      ;; Northern Hemisphere
      (cond
        (and (jt/after? local-date march-equinox)
             (jt/before? local-date june-solstice)) "spring"
        (and (jt/after? local-date june-solstice)
             (jt/before? local-date september-equinox)) "summer"
        (and (jt/after? local-date september-equinox)
             (jt/before? local-date december-solstice)) "autumn"
        :else "winter")
      ;; Southern Hemisphere
      (cond
        (and (jt/after? local-date march-equinox)
             (jt/before? local-date june-solstice)) "autumn"
        (and (jt/after? local-date june-solstice)
             (jt/before? local-date september-equinox)) "winter"
        (and (jt/after? local-date september-equinox)
             (jt/before? local-date december-solstice)) "spring"
        :else "summer"))))


(defn instant->local-date-time
  ([instant]
   (instant->local-date-time instant default-zone-id))
  ([instant zone-id]
   (.toLocalDateTime (.atZone instant zone-id))))


(defn now
  ([] (now default-zone-id))
  ([zone-id] (jt/instant zone-id)))


(defn unix-ts
  []
  (inst-ms (now)))


(defn human-time-ago
  ([epoch-milli]
   (human-time-ago (jt/instant epoch-milli) (now)))
  ([inst1 inst2]
   (h/datetime
     (instant->local-date-time inst1)
     (instant->local-date-time inst2))))


(def time-of-day
  (let [script "public/js/vendor/suncalc.js"
        script (str (fs/canonicalize (io/resource script)))
        api (polyglot/js-api script "SunCalc" [:getTimeOfDay])]
    (fn [local-date-time lat lng]
      ((:getTimeOfDay api) (->iso8601 local-date-time) lat lng))))


(def lunar-phase
  (let [script "public/js/vendor/lunarphase.js"
        script (str (fs/canonicalize (io/resource script)))
        fs [:lunarPhaseEmoji :lunarPhase]
        api (polyglot/js-api script "lunarPhase" fs)]
    (fn [local-date-time type]
      (let [iso8601 (->iso8601 local-date-time)]
        (case type
          :emoji ((:lunarPhaseEmoji api) iso8601)
          :string ((:lunarPhase api) iso8601))))))


;; Scratch
