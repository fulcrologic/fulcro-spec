(ns untangled-spec.async-spec
  #?(:clj
      (:require [untangled-spec.core :as c
                 :refer [specification behavior provided
                         with-timeline async tick assertions]]
                [clojure.test :refer [is]]
                [untangled-spec.async :as async]
                ))
  #?(:cljs (:require-macros [cljs.test :refer [is]]))
  #?(:cljs (:require
             [untangled-spec.async :as async]
             [untangled-spec.core
              :refer-macros [specification behavior provided
                             with-timeline async tick assertions]]
             )
           )
  )

(defn mock-fn1 [] (identity 0))
(defn mock-fn2 [] (identity 0))
(defn mock-fn3 [] (identity 0))

(specification "async-queue"
               (behavior "associates an event correctly with its time"
                         (let [queue (async/make-async-queue)]

                           (async/schedule-event queue 1044 mock-fn3)
                           (async/schedule-event queue 144 mock-fn2)
                           (async/schedule-event queue 44 mock-fn1)

                           (is (= mock-fn1 (:fn-to-call (get @(:schedule queue) 44))))
                           (is (= mock-fn2 (:fn-to-call (get @(:schedule queue) 144))))
                           (is (= mock-fn3 (:fn-to-call (get @(:schedule queue) 1044))))
                           (is (= 44 (:abs-time (get @(:schedule queue) 44))))
                           (is (= 144 (:abs-time (get @(:schedule queue) 144))))
                           (is (= 1044 (:abs-time (get @(:schedule queue) 1044))))
                           ))


               (behavior "keeps events in order"
                         (let [queue (async/make-async-queue)]

                           (async/schedule-event queue 1044 mock-fn3)
                           (async/schedule-event queue 144 mock-fn2)
                           (async/schedule-event queue 44 mock-fn1)

                           (is (= [44 144 1044] (keys @(:schedule queue))))
                           )
                         )



               (behavior "refuses to add events that collide in time"
                         (let [queue (async/make-async-queue)]

                           (async/schedule-event queue 44 identity)

                           #?(:clj  (is (thrown-with-msg? java.lang.Exception #"already contains an event" (async/schedule-event queue 44 identity)))
                              :cljs (is (thrown-with-msg? js/Error #"already contains an event" (async/schedule-event queue 44 identity)))
                              )
                           )
                         )

               (behavior "associates an event correctly with its time relative to current-time"
                         (let [queue (async/make-async-queue)]
                           (async/schedule-event queue 44 mock-fn1)

                           (async/advance-clock queue 10)
                           (async/schedule-event queue 44 mock-fn2)

                           (is (= mock-fn1 (:fn-to-call (get @(:schedule queue) 44))))
                           (is (= mock-fn2 (:fn-to-call (get @(:schedule queue) 54))))
                           (is (= 44 (:abs-time (get @(:schedule queue) 44))))
                           (is (= 54 (:abs-time (get @(:schedule queue) 54))))
                           )
                         )

               (behavior "executes and removes events as clock advances"
                         (let [detector (atom false)
                               detect (fn [] (reset! detector true))
                               queue (async/make-async-queue)]
                           (async/schedule-event queue 44 detect)
                           (async/schedule-event queue 144 mock-fn1)

                           (async/advance-clock queue 50)

                           (is @detector)
                           (is (not (nil? (async/peek-event queue))))
                           )
                         )

               (behavior "advance clock just advances the time with no events"
                         (let [queue (async/make-async-queue)]

                           (async/advance-clock queue 1050)

                           (is (= 1050 (async/current-time queue)))
                           )
                         )

               (behavior "passes exceptions through to caller of advance-clock"
                         (let [queue (async/make-async-queue)
                               thrower
                               #?(:cljs (fn [] (throw (js/Error. "Bummer!")))
                                  :clj  (fn [] (throw (java.lang.Exception. "Bummer!"))))
                               ]
                           (async/schedule-event queue 10 thrower)
                           #?(:clj  (is (thrown? java.lang.Exception (async/advance-clock queue 100)))
                              :cljs (is (thrown? js/Error (async/advance-clock queue 100))))
                           )
                         )

               (behavior "triggers events in correct order when a triggered event adds to queue"
                         (let [queue (async/make-async-queue)
                               invocations (atom 0)         ;how many functions have run
                               add-on-fn (fn []             ; scheduled by initial function (just below this one) 10ms AFTER it runs (abs of 11ms)
                                           (is (= 11 (async/current-time queue)))
                                           (is (= 1 @invocations))
                                           (swap! invocations inc))
                               trigger-adding-evt (fn []    ; scheduled below to run at 1ms
                                                    (is (= 0 @invocations))
                                                    (is (= 1 (async/current-time queue)))
                                                    (swap! invocations inc)
                                                    (async/schedule-event queue 10 add-on-fn)
                                                    )
                               late-fn (fn []               ; manually scheduled at 15ms...must run AFTER the one that was added during the trigger
                                         (is (= 15 (async/current-time queue)))
                                         (is (= 2 @invocations))
                                         )
                               ]

                           (async/schedule-event queue 1 trigger-adding-evt)
                           (async/schedule-event queue 15 late-fn)
                           (async/advance-clock queue 100)
                           ;; see assertions in functions...
                           )
                         )
               )


(specification "processing-an-event"
               (behavior "executes the first scheduled item"
                         (let [detector (atom false)
                               detect (fn [] (reset! detector true))
                               queue (async/make-async-queue)]
                           (async/schedule-event queue 44 detect)

                           (async/process-first-event! queue)

                           (is @detector)
                           ))


               (behavior "removes the first scheduled item"
                         (let [queue (async/make-async-queue)]
                           (async/schedule-event queue 44 mock-fn1)

                           (async/process-first-event! queue)

                           (is (= 0 (count @(:schedule queue))))

                           )
                         ))

