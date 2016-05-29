(ns untangled-spec.core-spec
  (:require [untangled-spec.core
             :refer [specification behavior provided assertions]
             :as core]
            [clojure.test :as t :refer [is]]
            [untangled-spec.contains :refer [*contains?]]))

(specification "adds methods to clojure.test/assert-expr"
  (assertions
    (methods t/assert-expr)
    =fn=> (*contains? '[= exec throws?] :keys)))
(specification "var-name-from-string"
  (assertions
    "allows the following"
    (core/var-name-from-string "asdfASDF1234!#$%&*|:<>?")
    =fn=> #(not (re-find #"\-" (str %)))
    "converts the rest to dashes"
    (core/var-name-from-string "\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß")
    =fn=> #(re-matches #"__\-+__" (str %))))
