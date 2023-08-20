(ns vortext.esther.util.emoji
  (:require [clojure.string :as str])
  (:import [net.fellbaum.jemoji EmojiManager]
           [org.ahocorasick.trie Trie]))


(defonce emojis (map bean (EmojiManager/getAllEmojis)))

(defn emoji? [s]
  (EmojiManager/isEmoji ^String s))

(defn emoji-in-str [s]
  (map bean (EmojiManager/extractEmojisInOrder ^String s)))



(def alias->unicode
  (->> emojis
       (mapcat (fn [emoji]
                 (map #(vector % (:unicode emoji))
                      (:allAliases emoji))))
       (into {})))

(defn build-trie
  [alias-map]
  (let [builder (Trie/builder)]
    (doseq [[alias _] alias-map]
      (.addKeyword builder alias))
    (.build builder)))

(defonce trie (build-trie alias->unicode))

(defn parse-to-unicode
  ([text] (parse-to-unicode trie alias->unicode text))
  ([trie alias->unicode text]
   (when-not (str/blank? text)
     (let [emits (.parseText trie text)
           sorted-emits (sort-by #(.getStart %) emits)]
       (loop [remaining-emits sorted-emits
              prev-end 0
              result ""]
         (if (empty? remaining-emits)
           (str result (subs text prev-end))
           (let [emit (first remaining-emits)
                 start (.getStart emit)
                 end (inc (.getEnd emit))
                 replacement (get alias->unicode (.getKeyword emit))]
             (recur (rest remaining-emits)
                    end
                    (str result (subs text prev-end start) replacement)))))))))
