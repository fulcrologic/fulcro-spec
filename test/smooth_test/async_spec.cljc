(ns ^:figwheel-always smooth-test.async-spec
  #?(:clj
     (:require [smooth-test.async :as async]
               [clojure.test :as t
                :refer (is deftest with-test run-tests testing)]))
  #?(:cljs (:require-macros [cljs.test :refer (is deftest run-tests)]))
  #?(:cljs (:require [smooth-test.async :as async]
             [cljs.test :as t])))

(defn mock-fn1 [] (identity 0))
(defn mock-fn2 [] (identity 0))
(defn mock-fn3 [] (identity 0))

(deftest async-queue-associates-an-event-correctly-with-its-time
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
    )
  )

(deftest async-queue-keeps-events-in-order
  (let [queue (async/make-async-queue)]
    (async/schedule-event queue 1044 mock-fn3)
    (async/schedule-event queue 144 mock-fn2)
    (async/schedule-event queue 44 mock-fn1)

    (is (= [44 144 1044] (keys @(:schedule queue))))
    )
  )

(deftest async-queue-refuses-to-add-events-that-collide-in-time
  (let [queue (async/make-async-queue)]
    (async/schedule-event queue 44 identity)

    #?(:clj  (is (thrown-with-msg? java.lang.Exception #"already contains an event" (async/schedule-event queue 44 identity)))
       :cljs (is (thrown-with-msg? js/Error #"already contains an event" (async/schedule-event queue 44 identity)))
       )
    )
  )


(deftest async-queue-associates-an-event-correctly-with-its-time-relative-to-current-time
  (let [queue (async/make-async-queue)]
    (async/schedule-event queue 44 mock-fn1)
    (async/advance-clock queue 100)
    (async/schedule-event queue 44 mock-fn2)

    (is (= mock-fn1 (:fn-to-call (get @(:schedule queue) 44))))
    (is (= mock-fn2 (:fn-to-call (get @(:schedule queue) 144))))
    (is (= 44 (:abs-time (get @(:schedule queue) 44))))
    (is (= 144 (:abs-time (get @(:schedule queue) 144))))
    )
  )
