(ns untangled-spec.reporters.suite-spec
  (:require [untangled-spec.core
             :refer [specification behavior provided assertions]]
            [untangled-spec.reporters.suite]
            [cljs.test :as t]
            [untangled-spec.contains :refer [*contains?]]))

(specification "adds methods to cljs.test/assert-expr"
  (assertions
    (methods t/assert-expr)
    =fn=> (*contains? '[= exec throws?] :keys)))
