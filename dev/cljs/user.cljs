(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)])
  (:require [untangled-spec.dom.suite :as ts :include-macros true]
            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.report
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec
            [untangled-spec.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

(defn run-all-tests []
  )

(defn on-load []
  (run-all-tests)
  )

(ts/test-suite dom-report
               'untangled-spec.async-spec
               'untangled-spec.stub-spec
               'untangled-spec.provided-spec
               'untangled-spec.timeline-spec
               'untangled-spec.dom.events-spec
               'untangled-spec.dom.util-spec
               )

