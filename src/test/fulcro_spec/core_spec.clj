(ns fulcro-spec.core-spec
  (:require
    [clojure.test :as t :refer [is deftest]]
    [fulcro-spec.core
     :refer [behavior when-mocking assertions]
     :as core]))

(deftest var-name-from-string
  (assertions
    "allows the following"
    (core/var-name-from-string "asdfASDF1234!#$%&*|:<>?")
    =fn=> #(not (re-find #"\-" (str %)))
    "converts the rest to dashes"
    (core/var-name-from-string "\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß")
    =fn=> #(re-matches #"__\-+__" (str %))))
