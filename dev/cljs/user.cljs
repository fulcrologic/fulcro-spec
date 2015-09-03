(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)])
  (:require smooth-test.async-spec
            smooth-test.stub-spec
            smooth-test.provided-spec
            smooth-test.report
            smooth-test.timeline-spec
            [smooth-test.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

(defn run-all-tests []
  (run-tests (cljs.test/empty-env :smooth-test.report/console) 'smooth-test.async-spec)
  (run-tests (cljs.test/empty-env :smooth-test.report/console) 'smooth-test.stub-spec)
  (run-tests (cljs.test/empty-env :smooth-test.report/console) 'smooth-test.provided-spec)
  (run-tests (cljs.test/empty-env :smooth-test.report/console) 'smooth-test.timeline-spec)
  )

(defn on-load []
  (run-all-tests)
  )

