(ns ^:figwheel-always cljs.user
  (:require-macros [untangled-spec.reporters.suite
                    :refer [deftest-suite]])
  (:require untangled-spec.reporters.console
            untangled-spec.reporters.browser
            untangled-spec.reporters.impl.suite

            [cljs.test :refer-macros [run-tests]]

            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec
            untangled-spec.reporters.browser-spec
            ))

(enable-console-print!)

(deftest-suite spec-report
  'untangled-spec.async-spec
  'untangled-spec.stub-spec
  'untangled-spec.provided-spec
  'untangled-spec.timeline-spec
  'untangled-spec.dom.events-spec
  'untangled-spec.dom.util-spec
  'untangled-spec.reporters.browser-spec
  )

(defn on-load []
  (spec-report))

(spec-report)
