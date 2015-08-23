(ns smooth-test.behavior
  (:require [smooth-test.core :refer [boo]]))

(defn convert-assertions [forms]
  (if (not= 0 (mod (count forms) 3))
    (throw (ex-info (str "Syntax error in assertions" forms) {})))
  (let [assertions (partition-all 3 forms)]
    (vec (map #(assoc {} :expression `(fn [] ~(first %))
                         ;:arrow (second %)
                         :expected `(fn [] ~(last %))) assertions))
    )
  )

(defmacro behavior [title prov value & forms]
  (boo)
  `(assoc {} :title ~title
             :capture (fn [~'tst] (with-redefs [~prov (fn [] ~value)] (~'tst)))
             :assertions ~(convert-assertions forms))
  )

