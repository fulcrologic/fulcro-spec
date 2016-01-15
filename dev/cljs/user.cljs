(ns ^:figwheel-always cljs.user
  (:require untangled-spec.tests-to-run
            [untangled-spec.reporters.suite
             :refer-macros [deftest-all-suite]]))

(enable-console-print!)

(deftest-all-suite spec-report #"untangled.*-spec")

(def on-load spec-report)

(spec-report)
