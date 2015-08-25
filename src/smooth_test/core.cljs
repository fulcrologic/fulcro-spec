(ns smooth-test.core
  (:require cljs.pprint smooth-test.behavior)
  )

(defn advance-clock [tm]
  (cljs.pprint/pprint (str "Advancing clock " tm "ms")))

(defn run-assertion [assertion]
  (let [actual ((:action assertion))
        expected ((:expected assertion))]
    (if (= actual expected)
            (cljs.pprint/pprint "TEST OK.")
            (cljs.pprint/pprint (str "TEST FAILED. Expected " expected " but got " actual))
            )
    )
  )

(defn run [test]
  ;(cljs.pprint/pprint test)
  ((:setup test)
   (doseq [step (-> test :steps)]
     (cond
       (= :assertion (:type step)) (run-assertion step)
       (= :clock-tick (:type step)) (advance-clock (:time step))
       ))))

(defn boo [] 3)
