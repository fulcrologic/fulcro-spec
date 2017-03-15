(ns untangled-spec.reporters.suite
  (:require
    [untangled-spec.suite :as ts]))

(defmacro deftest-all-suite [suite-name regex & [selectors]]
  `(ts/def-test-suite ~suite-name ~regex ~(or selectors {})))
