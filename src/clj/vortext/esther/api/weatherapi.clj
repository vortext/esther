(ns vortext.esther.api.weatherapi
  (:require
   [clj-http.client :as client]
   [clojure.core.memoize :as memoize]
   [camel-snake-kebab.core :as csk]
   [vortext.esther.secrets :refer [secrets]]
   [clojure.tools.logging :as log]))

(def uri "https://api.weatherapi.com/v1/current.json")

(def weatherapi-current
  (memoize/ttl
   (fn [q]
     (:body
      (client/get
       uri
       {:accept :json
        :as :json
        :query-params {"q" q
                       "key" (:weatherapi-api-key (secrets))}})))
   ;; 3 hours
   :ttl/threshold (* 3 60 60 1000)))

(def condition-text-ks
  [:condition :text])

(defn current-weather
  [location]
  (try
    (let [current-weather (:current (weatherapi-current location))]
      (csk/->kebab-case (get-in current-weather condition-text-ks)))
    (catch Exception _ {})))
