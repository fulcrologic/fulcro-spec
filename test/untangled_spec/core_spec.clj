(ns untangled-spec.core-spec
  (:require [untangled-spec.core
             :refer [specification behavior provided assertions]]
            [clojure.test :as t :refer [is]]
            [untangled-spec.contains :refer [*contains?]]))

(specification "untangled-spec.core-spec"
  (behavior "adds methods to clojure.test/assert-expr"
    (assertions
      (methods t/assert-expr)
      =fn=> (*contains? '[= exec throws?] :keys))))
