(ns ^:figwheel-always cljs.user
  (:require untangled-spec.reporters.console
            untangled-spec.reporters.browser
            untangled-spec.reporters.impl.suite

            [untangled-spec.reporters.suite :refer-macros [deftest-suite]]
            [cljs.test :refer-macros [run-tests]]

            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec
            ))

(enable-console-print!)

(deftest-suite spec-report
  'untangled-spec.async-spec
  'untangled-spec.stub-spec
  'untangled-spec.provided-spec
  'untangled-spec.timeline-spec
  'untangled-spec.dom.events-spec
  'untangled-spec.dom.util-spec
  )

(defn on-load []
  (spec-report))

(spec-report)
