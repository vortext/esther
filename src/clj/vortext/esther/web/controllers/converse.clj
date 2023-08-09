(ns vortext.esther.web.controllers.converse
  (:require
   [ring.util.http-response :as http-response])
  (:import
   [java.util Date]))

(def example-response
  {:response "Earum blanditiis molestias explicabo in id. Repellat est veniam nihil quia et. Commodi culpa in voluptatem eveniet doloribus velit doloribus nulla. Consequatur natus dolor necessitatibus cum blanditiis asperiores nihil. Neque ducimus modi occaecati sint.

Omnis in ipsam sapiente delectus. Sapiente delectus fugiat quia odio ipsam et quo aut. Laboriosam voluptatibus reiciendis eos quia autem voluptatem vel numquam. Quis minima et iure qui. Doloremque mollitia rem ut numquam veritatis aut ipsum. Praesentium laborum beatae suscipit. 🌌😊"})

(defn process
  [req]
  (Thread/sleep (+ 100000 (int (rand 500))))
  {:request req
   :response example-response}
  )

(defn converse!
  [req]
  (http-response/ok (process req)))
