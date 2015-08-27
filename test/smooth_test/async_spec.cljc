(ns ^:figwheel-always smooth-test.async-spec
  #?(:clj
     (:require [smooth-test.async :as async]
               [clojure.test :as t
                :refer (is deftest with-test run-tests testing)]))
  #?(:cljs (:require-macros [cemerick.cljs.test
                             :refer (is deftest with-test run-tests testing test-var)]))
  #?(:cljs (:require [smooth-test.async :as async]
             [cemerick.cljs.test :as t])))

(defn mock-fn1 [] (identity 0))
(defn mock-fn2 [] (identity 0))
(defn mock-fn3 [] (identity 0))

(deftest async-queue-associates-an-event-correctly-with-its-abs-time
  (let [queue (async/make-async-queue)]
    (async/schedule-event queue 1044 mock-fn3)
    (async/schedule-event queue 144 mock-fn2)
    (async/schedule-event queue 44 mock-fn1)

    (is (= mock-fn2 (get @(:schedule queue) 44)))
    (is (= mock-fn2 (get @(:schedule queue) 144)))
    (is (= mock-fn3 (get @(:schedule queue) 1044)))
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
