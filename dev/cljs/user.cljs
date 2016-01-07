(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is run-tests testing)])
  (:require [untangled-spec.dom.suite :as ts :include-macros true]
            untangled-spec.tests-to-run
            untangled-spec.report
            [untangled-spec.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

(ts/test-all-suite spec-report #"untangled.*-spec")

(def on-load spec-report)

(spec-report)
