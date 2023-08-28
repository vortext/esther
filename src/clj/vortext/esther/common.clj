(ns vortext.esther.common)

(defn parse-number
  [s]
  (when (re-find #"^-?\d+\.?\d*$" s)
    (read-string s)))

(defn update-value
  "Updates the given key in the given map. Uses the given function to transform the value, if needed."
  [key transform-fn m default-value]
  (let [value (get m key)
        transformed-value (transform-fn value)]
    (assoc m key (if transformed-value
                   transformed-value
                   (or (transform-fn (str value))
                       default-value)))))
