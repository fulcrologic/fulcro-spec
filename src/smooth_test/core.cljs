(ns smooth-test.core
  (:require cljs.pprint smooth-test.behavior)
  )

(defn run [test]
      (cljs.pprint/pprint test)
      (let [expr (-> test :assertions (get 0) :expression)]
           ((:capture test) expr))
      )

(defn boo [] 3)
