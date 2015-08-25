(ns smooth-test.behavior-spec
  (:require [smooth-test.behavior :as b :include-macros true]
            [smooth-test.core :refer [run]]
            cljs.pprint
            )
  )

(enable-console-print!)

(let [
      b2 (b/behavior "second"
                     clock-ticks => 100
                     (b/sample) => 10
                     )
      p1 (b/provided "if I force it to return 2" (b/sample) => 2 (b/behavior "then it better" (b/sample) => 2))
      ]
     (cljs.pprint/pprint p1)
     (cljs.pprint/pprint (run p1))
     ;(cljs.pprint/pprint (run b2))
     )
