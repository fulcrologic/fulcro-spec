(ns untangled-spec.all-tests
  (:require
    untangled-spec.tests-to-run
    [doo.runner :refer-macros [doo-all-tests]]))

(doo-all-tests #"untangled.*-spec")
