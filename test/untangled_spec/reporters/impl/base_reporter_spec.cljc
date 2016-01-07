(ns untangled-spec.reporters.impl.base-reporter-spec
  (:require [untangled-spec.reporters.impl.base-reporter :as base]
            [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided
              component assertions]]
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]])))

(specification
  "base-reporter"
  (component "make-test-result"
    (behavior "swaps message to assertion if type is error and there is not an assertion"
      (assertions
        (base/make-test-result :error {:message "monkey"}) => {:status :error :assertion "monkey"}
        (base/make-test-result :fail {:message "monkey"}) => {:status :fail :message "monkey"}
        (base/make-test-result :error {:message "monkey" :assertion "no swap"}) => {:status :error :message "monkey" :assertion "no swap"}))))
