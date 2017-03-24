(ns cljs.user
  (:require
    [clojure.spec.test :as st]
    [untangled-spec.tests-to-run]
    [untangled-spec.suite :as suite]
    [untangled-spec.selectors :as sel]))

(enable-console-print!)

;;optional, but can be helpful
(st/instrument)

;;define on-load as a fn that re-runs (and renders) the tests
;;for use by figwheel's :on-jsload
(suite/def-test-suite on-load {:ns-regex #"untangled-spec\..*-spec"}
  {:default #{::sel/none :focused}
   :available #{:focused :should-fail}})
