(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [vortext.esther.ai.openai :as openai]
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.tools.logging :as log]
   [ring.util.http-response :as http-response]))

(def state (atom nil))

(defn answer
  [history request]
  (let [{:keys [params]} request
        response (openai/complete history params)
        result {:response response
                :request params
                :ts (unix-ts)}]
    (reset! state [request response history])
    (log/info "REQUEST")
    (log/info (pprint/pprint params))
    (log/info "RESPONSE")
    (log/info (pprint/pprint response))
    (log/info "RESULT")
    (log/info (pprint/pprint result))
    result
    ))

(defn converse!
  [req]
  (http-response/ok (answer req)))

;; Scratch
(comment
  (defn example-response
    [params]
    {:response (str "😄 " (str params) (str/join "" (take (rand 250) "Earum blanditiis molestias explicabo in id. Repellat est veniam nihil quia et. Commodi culpa in voluptatem eveniet doloribus velit doloribus nulla. Consequatur natus dolor necessitatibus cum blanditiis asperiores nihil. Neque ducimus modi occaecati sint.

Omnis in ipsam sapiente delectus. Sapiente delectus fugiat quia odio ipsam et quo aut. Laboriosam voluptatibus reiciendis eos quia autem voluptatem vel numquam. Quis minima et iure qui. Doloremque mollitia rem ut numquam veritatis aut ipsum. Praesentium laborum beatae suscipit. 🌌😊")))})

  (def *history (atom nil))

  (defn answer
    [request]
    (let [{:keys [session params]} request
          _history (:history session)]
      (reset! *history _history)
      (Thread/sleep (+ 1250 (int (rand 500))))
      (merge
       {:response (example-response params)}
       {:request params
        :ts (unix-ts)}))))
