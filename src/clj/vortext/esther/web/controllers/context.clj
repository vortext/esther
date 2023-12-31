(ns vortext.esther.web.controllers.context
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [vortext.esther.util.json :as json]
    [vortext.esther.util.time :as time]))


(defonce timezones
  (-> "data/largest_city_by_timezones.json"
      (io/resource)
      (slurp)
      (json/read-json-value)))


(defn guess-location
  [timezone]
  (if-let [largest-city (get timezones (keyword timezone))]
    largest-city
    {:latitude 51.509 :longitude -0.118 :city "London"}))


(defn from-client-context
  [{:keys [location iso8601 timezone]}]
  (let [timezone (or timezone time/default-zone-id)
        has-location? (boolean location)
        location (if has-location? location (guess-location timezone))
        {:keys [latitude longitude]} location
        now (time/iso8601->offset-date-time iso8601)
        present (time/->local-date-time now timezone)]
    {:context/present present
     :context/time-of-day (time/time-of-day present latitude longitude)
     :context/today (time/human-today present time/default-locale)
     :context/lunar-phase (time/lunar-phase present :emoji)
     :context/season (time/season now latitude)}))
