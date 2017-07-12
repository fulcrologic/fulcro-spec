(ns cljs.user
  (:require
    [clojure.spec.test.alpha :as st]
    [fulcro-spec.tests-to-run]
    [fulcro-spec.suite :as suite]
    [fulcro-spec.selectors :as sel]))

(enable-console-print!)

;;optional, but can be helpful
(st/instrument)

;;define on-load as a fn that re-runs (and renders) the tests
;;for use by figwheel's :on-jsload
(suite/def-test-suite on-load {:ns-regex #"fulcro-spec\..*-spec"}
  {:default #{::sel/none :focused}
   :available #{:focused :should-fail}})
