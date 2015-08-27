(ns smooth-test.behavior-spec
  (:require [smooth-test.behavior :as b :include-macros true]
            [smooth-test.specification-spec]
            cljs.core
            cljs.pprint
            )
  )

(enable-console-print!)
(defn other [] (+ 1 1))
(defn andanother [] 1)
(defn sample [] (* 5 (other)))
(defn sample2 [] (* 5 (other) (andanother)))


(let [
      ;b2 (b/behavior "second"
      ;               clock-ticks => 100
      ;               (b/sample) => 10
      ;               )
      ;p1 (b/provided "if I force it to return 2"
      ;               (b/sample) => 2
      ;               (b/behavior "then it better"
      ;                           (b/sample) => 2))
      ;p2 (b/provided "if I force other function to return 4 instead of 2"
      ;               (b/other) => 4
      ;               (b/behavior "then it better"
      ;                           (b/sample) => 20))
      ;p3 (b/provided "if I force other function to return 4 and a second function to return 2"
      ;               (b/other) => 4
      ;               (b/andanother) => 2
      ;               (b/behavior "then it better"
      ;                           (b/sample2) => 40))
      ]
  ;; TODO: The setup is getting parsed ok, but I'm having trouble getting it to bind...
  ;(cljs.pprint/pprint "provided output")
  ;(cljs.pprint/pprint p3)
  ;(cljs.pprint/pprint "run provided")
  ;(run-with-setup p3)
  ;  ;(cljs.pprint/pprint "run behavior")
  ;(cljs.pprint/pprint (run b2))
  )
