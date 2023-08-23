(ns vortext.esther.ai.hnswlib
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs])
  (:import
   [com.github.jelmerk.knn DistanceFunctions]
   [com.github.jelmerk.knn.hnsw HnswIndex]
   [com.github.jelmerk.knn.util VectorUtils]
   [java.io BufferedReader InputStreamReader]
   [java.util.zip GZIPInputStream]))

(defrecord Item [id vector]
  com.github.jelmerk.knn.Item
  (id [this] id)
  (vector [this] vector)
  (dimensions [this] (count vector)))


(defn parse-line [line]
  (let [[word & rest] (str/split line #" ")
        vector (float-array (map #(Float/parseFloat %) rest))]
    {:id word :vector (VectorUtils/normalize vector)}))

(defn load-word-vectors [path]
  (with-open [reader (-> path
                         io/input-stream
                         GZIPInputStream.
                         (java.io.BufferedInputStream. 8192) ; Increased buffer size
                         InputStreamReader.
                         BufferedReader.)]
    (->> reader
         line-seq
         (drop 1)
         (pmap parse-line) ; Parallel processing
         doall)))


(defn create-hnsw-index [items]
  (let [distance-fn DistanceFunctions/FLOAT_INNER_PRODUCT
        dim 300
        index-builder (-> (HnswIndex/newBuilder dim distance-fn (count items))
                          (.withM 16)
                          (.withEf 200)
                          (.withEfConstruction 200))
        item-objects (map (fn [{:keys [id vector]}] (->Item id vector)) items)
        index (.build index-builder)]
    (.addAll index item-objects)
    index))



(defn find-neighbors [index word k]
  (->> (.findNeighbors index word k)
       (map (fn [result]
              {:id (.id (.item result))
               :distance (.distance result)}))))



;; Example usage:
;;
;; (def file-path (str (fs/canonicalize (fs/path "/array/Models/word-embeddings/cc.en.300.vec.gz"))))
;; (def words (load-word-vectors file-path))
;; (def index (create-hnsw-index words))
;; (find-neighbors index "example" 10)
