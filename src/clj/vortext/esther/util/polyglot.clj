;; "Inspired" by https://github.com/wavejumper/clj-polyglot
(ns vortext.esther.util.polyglot
  (:refer-clojure :exclude [import load-file load-string eval eval])
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log])
  (:import
   (org.graalvm.polyglot
    Context
    Source
    Value)
   (org.graalvm.polyglot.proxy
    ProxyArray
    ProxyObject)))


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


(defn serialize-arg
  [arg]
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


(defn source
  [lang path]
  (.build (Source/newBuilder lang (io/file path))))


(defn context-builder
  [lang]
  (doto (Context/newBuilder (into-array String [lang]))
    #_(.option "js.timer-resolution" "1")
    #_(.option "js.java-package-globals" "false")
    #_(.out System/out)
    #_(.err System/err)
    #_(.allowAllAccess true)
    (.allowNativeAccess true)))


(defn create-ctx
  [lang src]
  (let [context (.build (context-builder lang))
        _result (.eval context (source lang src))]
    context))


(defn print-global-keys
  [lang context]
  (let [bindings (.getBindings context lang)]
    (doseq [key (.getMemberKeys bindings)]
      (println key))))


(defn from
  [^String lang ^Context ctx ^String module-name]
  (let [bindings (.getBindings ctx lang)]
    ^Value (.getMember bindings module-name)))


(defn import
  ([^Value member members]
   (into {}
         (map (fn [f]
                [f (.getMember member (name f))]))
         members))
  ([^Value value api-name members]
   (let [member (.getMember value (name api-name))]
     (into {}
           (map (fn [f]
                  [f (.getMember member (name f))]))
           members))))


(defn eval
  [value & args]
  (let [result (.execute ^Value value (into-array Object (map serialize-arg args)))]
    (deserialize result)))


(defn lang-api
  [lang src api-name api-fns]
  (let [ctx (create-ctx lang src)
        obj (from lang ctx api-name)
        api (import obj api-fns)]
    (into {} (map (fn [[k _]] [k (partial eval (get api k))]) api))))


(defn js-api
  [src api-name api-fns]
  (lang-api "js" src api-name api-fns))
