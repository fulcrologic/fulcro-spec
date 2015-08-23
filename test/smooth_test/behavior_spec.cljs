(ns smooth-test.behavior-spec
  (:require [smooth-test.behavior :as b :include-macros true]
            [smooth-test.core :refer [run]]
            cljs.pprint
            )
  )

(enable-console-print!)

(let [
      b2 (b/behavior "second" b/other 11
                     (b/sample) => 20
                     )
      expr2 (-> b2 :assertions (get 0) :expression)
      ]
     (cljs.pprint/pprint (run b2))
     ;(cljs.pprint/pprint (run expr2))
     )
