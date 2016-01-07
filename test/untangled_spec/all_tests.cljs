(ns untangled-spec.all-tests
  (:require
    [untangled-spec.test-symbols :as ts]
    [doo.runner :refer-macros [doo-all-tests]]
    [cljs.test]))

(doo-all-tests #"untangled.*")
