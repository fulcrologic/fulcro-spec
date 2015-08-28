(ns smooth-test.core
  #?(:cljs (:require [cljs.pprint :refer [pprint]])
     :clj  (:require [clojure.pprint :refer [pprint]])
     )
  )

(defn print-title [level text]
  (println (str (apply str (repeat (* level 3) " ")) text))
  )

(defn mock [rv] (fn [&rest] rv))

(defn run-specification [tests]
  (loop [t tests]
    (if (empty? t)
      nil
      (do
        ((first t) 1)
        (recur (rest t))
        )))
  )

(defn run-behaviors [level behaviors]
  (loop [t behaviors]
    (if (empty? t)
      nil
      (do
        ((first t) level)
        (recur (rest t))
        )))
  )

(defn run-behaviors-with-provided [level setupfn behaviors]
  (loop [t behaviors]
    (if (empty? t)
      nil
      (do
        (setupfn (first t) level)
        (recur (rest t))
        )))
  )
