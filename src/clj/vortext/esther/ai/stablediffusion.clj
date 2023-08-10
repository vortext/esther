(ns vortext.esther.ai.stablediffusion
  (:require [vortext.esther.config :refer [secrets]]))

(defonce api-key
  (:stablediffusion-api-key
   (secrets)))
