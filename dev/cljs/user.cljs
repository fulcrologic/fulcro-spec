(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)])
  (:require untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.report
            untangled-spec.timeline-spec
            [untangled-spec.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

(defn run-all-tests []
  (run-tests (cljs.test/empty-env :untangled-spec.report/console) 'untangled-spec.async-spec)
  (run-tests (cljs.test/empty-env :untangled-spec.report/console) 'untangled-spec.stub-spec)
  (run-tests (cljs.test/empty-env :untangled-spec.report/console) 'untangled-spec.provided-spec)
  (run-tests (cljs.test/empty-env :untangled-spec.report/console) 'untangled-spec.timeline-spec)
  )

(defn on-load []
  (run-all-tests)
  )

