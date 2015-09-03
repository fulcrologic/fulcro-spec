(ns smooth-test.timeline-spec
  #?(:clj
     (:require [smooth-test.core :as c :refer [with-timeline provided event tick specification behavior]]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               ))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
                            [smooth-test.core :refer [with-timeline provided async tick specification behavior]]
             ))
  #?(:cljs (:require [cljs.test :refer [do-report]]
             )
     )
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )


#?(:cljs
   (specification "Timeline"
     (behavior "within a timeline"
       (with-timeline
         (let [detector (atom [])]
           (provided
             (js/setTimeout f n) =3x=> (async n (f))

             (js/setTimeout (fn [] (js/setTimeout (fn [] (swap! detector conj "LAST")) 300) (swap! detector conj "FIRST")) 100)

             (behavior "nothing called until timer moves past first specified event is to occur"
               (is (= 0 (count @detector)))
               )

             (behavior "after first tick only the callbacks that satisfy the"
               (tick 101)
               (is (= 1  (count @detector)))
               )

             (behavior "more functions can run before next callback is called"
               (swap! detector conj "SECOND")
               (is (= 2 (count @detector)))
               )

             (behavior "after all time is passed all callback timers are fired"
               (tick 401)
               (is (= 3 (count @detector)))
               (is (= "FIRST" (first @detector)))
               (is (= "SECOND" (second @detector)))
               (is (= "LAST" (last @detector)))
               )
             )
           )
         )
       )
     )
   )
