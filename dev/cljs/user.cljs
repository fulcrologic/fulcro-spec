(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)]
                   [untangled-spec.reporters.suite :refer [def-test-suite]])
  (:require untangled-spec.reporters.console
            untangled-spec.reporters.browser
            untangled-spec.reporters.impl.suite
            [cljs.test :as t]

            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec
            ))

(enable-console-print!)

(def-test-suite spec-report
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
