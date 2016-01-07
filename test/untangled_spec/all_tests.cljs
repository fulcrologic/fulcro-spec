(ns untangled-spec.all-tests
  (:require
    untangled-spec.test-symbols
    [doo.runner :refer-macros [doo-all-tests]]))

(doo-all-tests #"untangled.*-spec")
