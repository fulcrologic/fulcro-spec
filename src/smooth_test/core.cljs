(ns smooth-test.core
  (:require cljs.pprint smooth-test.behavior)
  )

(defn print-title [level text]
  (println (str (apply str (repeat (* level 3) " ")) text))
  )


(defn advance-clock [level tm]
  (print-title level (str "Advancing clock " tm "ms")))

(defn run-assertion [level assertion]
  (let [actual ((:action assertion))
        expected ((:expected assertion))]
    (if (= actual expected)
            (print-title level "TEST OK.")
            (print-title level (str "TEST FAILED. Expected " expected " but got " actual))
            )
    )
  )

;(defn run-behaviors-with-provided [level test]
;  (letfn [(runner [] (run-behaviors level test))]
;    ;((:setup test) runner)
;  )
; )
