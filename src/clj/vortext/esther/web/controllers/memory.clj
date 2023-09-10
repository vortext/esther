(ns vortext.esther.web.controllers.memory
  (:require
   [next.jdbc :as jdbc]
   [vortext.esther.secrets :as secrets]
   [buddy.core.hash :as hash]
   [vortext.esther.util :refer [random-base64 bytes->b64]]))

(defn see-keyword
  [query-fn tx user kw]
  (let [{:keys [uid secret]} (:vault user)
        {:keys [data iv]} (secrets/encrypt-for-sql kw secret)
        fingerprint (-> (hash/sha256 (str uid kw)) (bytes->b64))
        content {:uid uid :data data :iv iv :fingerprint fingerprint}]
    (query-fn tx :see-keyword content)
    fingerprint))

(defn remember!
  [opts user obj]
  (let [{:keys [connection query-fn]} (:db opts)
        {:keys [uid secret]} (:vault user)
        {:keys [:memory/events :memory/gid]} obj
        [_ response] events
        {:keys [data iv]} (secrets/encrypt-for-sql obj secret)
        conversation? (boolean (:event/conversation? response))
        memory {:gid gid
                :uid uid
                :data data
                :conversation conversation?
                :iv iv}]
    (jdbc/with-transaction [tx connection]
      (query-fn tx :push-memory memory)
      (doall
       (map (fn [kw]
              (let [fingerprint (see-keyword query-fn tx user kw)]
                (query-fn tx :associate-keyword
                          {:gid gid
                           :fingerprint fingerprint})))
            (get-in response [:event/content :keywords] []))))
    obj))

(defn construct-memories
  [user contents]
  (let [{:keys [_uid secret]} (:vault user)
        decrypt #(secrets/decrypt-from-sql % secret)]
    (map decrypt contents)))


(defn last-memories
  ([opts user]
   (last-memories opts user 10))
  ([opts user n]
   (let [{:keys [query-fn]} (:db opts)
         uid (get-in user [:vault :uid])]
     (construct-memories
      user
      (query-fn :last-n-memories {:uid uid :n n})))))

(defn recent-conversation
  ([opts user]
   (recent-conversation opts user 5))
  ([opts user n]
   (let [{:keys [query-fn]} (:db opts)
         uid (get-in user [:vault :uid])]
     (construct-memories
      user
      (query-fn :last-n-conversation-memories {:uid uid :n n})))))

(defn todays-non-archived-memories
  [opts user]
  (let [{:keys [query-fn]} (:db opts)
        uid (get-in user [:vault :uid])]
    (construct-memories
     user
     (query-fn :todays-non-archived-memories {:uid uid}))))

(defn archive-todays-memories
  [opts user]
  (let [{:keys [query-fn]} (:db opts)
        uid (get-in user [:vault :uid])]
    (query-fn :archive-todays-memories {:uid uid})))

(def lambdas
  {:week  1.6534e-6
   :day   1.1574e-5
   :hour  2.7701e-4
   :month 5.5181e-7})

(defn frecency-keywords
  "λ (lamda) is a decay constant that determines the rate of
    forgetting. The value of lambda will determine how quickly the
    frecency declines as recency increases. Larger values of λ will
    cause more rapid decay, while smaller values will result in slower
    decay."
  ([opts user]
   (let [lambda 0.001
         n 25]
     (frecency-keywords opts user lambda n)))
  ([opts user lambda n]
   (let [{:keys [query-fn]} (:db opts)
         {:keys [uid secret]} (:vault user)
         cols [:fingerprint :frecency :recency :frequency]
         decrypt (fn [kw] (merge {:value (secrets/decrypt-from-sql kw secret)}
                                 (select-keys kw cols)))
         query-params {:uid uid :n n :lambda (lambda lambdas)}]
     (map decrypt (query-fn :frecency-keywords query-params)))))

(defn wipe-all!
  [opts user]
  (let [{:keys [connection query-fn]} (:db opts)
        {:keys [uid]} (:vault user)]
    (query-fn :wipe-all-memory {:uid uid})))

(defn wipe-today!
  [opts user]
  (let [{:keys [query-fn]} (:db opts)
        {:keys [uid]} (:vault user)]
    (query-fn :wipe-todays-memory {:uid uid})))
