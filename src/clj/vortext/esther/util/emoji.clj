(ns vortext.esther.util.emoji
  (:require
   [clojure.string :as str])
  (:import
   [net.fellbaum.jemoji EmojiManager]
   [org.ahocorasick.trie Trie]))


(defonce emojis (map bean (EmojiManager/getAllEmojis)))

(defn emoji? [s]
  (EmojiManager/isEmoji ^String s))

(def emoji-pattern
  (re-pattern "[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F700}-\\x{1F77F}\\x{1F780}-\\x{1F7FF}\\x{1F800}-\\x{1F8FF}\\x{1F900}-\\x{1F9FF}\\x{1FA00}-\\x{1FA6F}\\x{1FA70}-\\x{1FAFF}]"))

(defn unicode-emoji? [s]
  (boolean (or (EmojiManager/isEmoji s)
               (re-matches emoji-pattern (str/trim s)))))

(defn emoji-in-str [s]
  (map bean (EmojiManager/extractEmojisInOrder ^String s)))

(defn- alias->unicode
  [k]
  (->> emojis
       (mapcat (fn [emoji]
                 (map #(vector % (:unicode emoji))
                      (k emoji))))
       (into {})))

(defn build-trie
  [alias-map]
  (let [builder (Trie/builder)]
    (doseq [[alias _] alias-map]
      (.addKeyword builder alias))
    (.build builder)))

(defn parse-to-unicode
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

(defn create-replace-fn
  [key]
  (let [alias-map (alias->unicode key)
        trie (build-trie alias-map)]
    (partial parse-to-unicode trie alias-map)))

(def replace-slack-aliasses (create-replace-fn :slackAliases))


(defn extract-first-emoji
  [s]
  (if (unicode-emoji? s) s
      (if-let [recovered (emoji-in-str (replace-slack-aliasses s))]
        (:emoji (first recovered)) nil)))
