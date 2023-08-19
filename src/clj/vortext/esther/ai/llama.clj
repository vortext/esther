(ns vortext.esther.ai.llama
  (:require
   [clojure.tools.logging :as log]
   [com.phronemophobic.llama :as llama]
   [com.phronemophobic.llama.raw :as raw]
   [vortext.esther.config :refer [errors]]
   [vortext.esther.util :refer
    [parse-maybe-json escape-newlines]]
   [clojure.string :as str])
  )

(defn generate-prompt
  [submission]
  (let [with-name #(if (= % "assistant") "esther" %)]
    (str (:content (first submission))
         (str/join
          "\n"
          (for [s (rest submission)]
            (str (with-name (:role s)) ":" (:content s) (llama/eos)))))))


(defn complete
  [context submission]
  (let [prompt (generate-prompt submission)
        _ (log/debug "llama::complete:prompt" prompt)
        s (llama/generate-string context prompt)
        maybe-json (subs
                    s
                    (str/index-of s "{")
                    (inc (str/last-index-of s "}")))]
    (log/debug "llama::complete:maybe-json" maybe-json)
    (if-let [json-obj? (parse-maybe-json (escape-newlines maybe-json))]
      json-obj?
      (:json-parse-error errors))
    maybe-json)
  )

(defn create-complete [{:keys [model-path options]}]
  (let [context (llama/create-context
                 model-path
                 (merge options {:n-gpu-layers 1
                                 :n-ctx 2048
                                 :use-mmap true}))]
    (partial complete context)))


;; Todo integrant
;; Todo make sure it works
