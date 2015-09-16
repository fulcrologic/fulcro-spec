(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)])
  (:require smooth-spec.async-spec
            smooth-spec.stub-spec
            smooth-spec.provided-spec
            smooth-spec.report
            smooth-spec.timeline-spec
            [smooth-spec.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

(defn run-all-tests []
  (run-tests (cljs.test/empty-env :smooth-spec.report/console) 'smooth-spec.async-spec)
  (run-tests (cljs.test/empty-env :smooth-spec.report/console) 'smooth-spec.stub-spec)
  (run-tests (cljs.test/empty-env :smooth-spec.report/console) 'smooth-spec.provided-spec)
  (run-tests (cljs.test/empty-env :smooth-spec.report/console) 'smooth-spec.timeline-spec)
  )

(defn on-load []
  (run-all-tests)
  )

