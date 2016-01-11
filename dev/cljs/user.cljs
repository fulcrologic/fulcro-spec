(ns ^:figwheel-always cljs.user
  (:require-macros [untangled-spec.reporters.suite :as ts])
  (:require untangled-spec.tests-to-run
            [untangled-spec.runner.browser :as b]
            untangled-spec.reporters.impl.suite
            [cljs.test :as t :include-macros true]))

(enable-console-print!)

(ts/deftest-all-suite spec-report #"untangled.*-spec")

(def on-load spec-report)

(spec-report)
