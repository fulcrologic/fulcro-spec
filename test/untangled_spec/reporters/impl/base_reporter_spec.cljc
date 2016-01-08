(ns untangled-spec.reporters.impl.base-reporter-spec
  (:require [untangled-spec.reporters.impl.base-reporter :as base]
            [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided
              component assertions]]
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]])))

(specification "base-reporter-spec"
  (component "tuplize"
    (behavior "yes"
      (assertions
        (base/tuplize {:A [1 {:B :D}
                           2 {:E :G}]})
        => {})))

  (component "mutations->paths"
    (behavior "maybe"
      (assertions
        (base/mutations->paths {:A [[1 {:B :D}]
                                    [2 {:E :G}]]})
        => {}))))
