(ns untangled-spec.reporters.impl.base-reporter-spec
  (:require [untangled-spec.reporters.impl.base-reporter :as base]
            [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided component assertions]]))

(specification "base-reporter-spec")
