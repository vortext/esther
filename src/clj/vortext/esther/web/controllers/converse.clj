(ns vortext.esther.web.controllers.converse
  (:require
   [vortext.esther.util.time :refer [unix-ts]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [ring.util.http-response :as http-response]))

(defn example-response
  []
  {:response (str "😄 " (str/join "" (take (rand 250) "Earum blanditiis molestias explicabo in id. Repellat est veniam nihil quia et. Commodi culpa in voluptatem eveniet doloribus velit doloribus nulla. Consequatur natus dolor necessitatibus cum blanditiis asperiores nihil. Neque ducimus modi occaecati sint.

Omnis in ipsam sapiente delectus. Sapiente delectus fugiat quia odio ipsam et quo aut. Laboriosam voluptatibus reiciendis eos quia autem voluptatem vel numquam. Quis minima et iure qui. Doloremque mollitia rem ut numquam veritatis aut ipsum. Praesentium laborum beatae suscipit. 🌌😊")))})

(defn answer
  [request]
  (let [{:keys [session params]} request]
    (log/info session)
    (Thread/sleep (+ 1250 (int (rand 500))))
    {:ts (unix-ts)
     :request params
     :response (example-response)}))

(defn converse!
  [req]
  (http-response/ok (answer req)))
