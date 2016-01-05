(ns untangled-spec.timeline-spec
  #?(:clj
      (:require [untangled-spec.core :as c :refer [with-timeline provided async tick specification behavior]]
                [clojure.test :as t :refer [is]]
                ))
  #?(:cljs (:require-macros [cljs.test :refer [is]]
                            ))
  #?(:cljs (:require
             [untangled-spec.core
              :refer-macros [with-timeline provided async
                             tick specification behavior]]
             ))
  #?(:clj
      (:import clojure.lang.ExceptionInfo))
  )


#?(:cljs
   (specification "Timeline"
     (behavior "within a timeline"
       (with-timeline
         (let [detector (atom [])]
           (provided "when mocking setTimeout"
             (js/setTimeout f n) =2x=> (async n (f))

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
               (tick 301)
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
