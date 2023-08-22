(ns vortext.esther.util.polyglot
  (:refer-clojure :exclude [import load-file load-string eval])
  (:require [clojure.tools.logging :as log])
  (:import (org.graalvm.polyglot Context Value)
           (org.graalvm.polyglot.proxy ProxyArray ProxyObject)))

(defn deserialize-number
  [^Value result]
  (cond
    (.fitsInShort result)
    (.asShort result)

    (.fitsInLong result)
    (.asLong result)

    (.fitsInInt result)
    (.asInt result)

    (.fitsInDouble result)
    (.asDouble result)

    (.fitsInFloat result)
    (.asFloat result)))

(defn deserialize
  [^Value result]
  (cond
    (.isNumber result)
    (deserialize-number result)

    (.isString result)
    (.asString result)

    (.hasArrayElements result)
    (let [n (.getArraySize result)]
      (into [] (map (fn [idx]
                      (deserialize (.getArrayElement result idx)))
                    (range 0 n))))

    (.isNull result)
    nil

    (.isBoolean result)
    (.asBoolean result)

    :else
    result))

(defn serialize-arg [arg]
  (cond
    (keyword? arg)
    (name arg)

    (symbol? arg)
    (name arg)

    (map? arg)
    (ProxyObject/fromMap (into {} (map (fn [[k v]]
                                         [(serialize-arg k) (serialize-arg v)])
                                       arg)))
    (coll? arg)
    (ProxyArray/fromArray (into-array Object (map serialize-arg arg)))

    :else
    arg))

(defn load-js [js-code]
  (let [context (Context/create (into-array String ["js"]))
        _result (.eval context "js" js-code)]
    context))

(defn print-global-keys [context]
  (let [bindings (.getBindings context "js")]
    (doseq [key (.getMemberKeys bindings)]
      (println key))))

(defn eval-js
  [value args]
  (let [result (.execute value (into-array Object (map serialize-arg args)))]
    (deserialize result)))


(defn js-object-fn
  [context object-name fn-name]
  (let [js-object (.getMember (.getBindings context "js") object-name)
        js-fn (.getMember js-object fn-name)]
    (fn [& args]
      (eval-js js-fn args))))



;; Scratch
(comment
  (def test-broken-json "{\"test\" \"test\"}")
  (def test-json "{\"test\":\"test\"}"))
