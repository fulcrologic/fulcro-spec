(ns fulcro-spec.core-spec
  (:require
    [clojure.test :refer [deftest]]
    [fulcro-spec.core
     :refer [=> assertions]
     :as core]))

(deftest var-name-from-string
  (assertions
    "converts the rest to dashes"
    (core/var-name-from-string "foo\\\"@^()[]{};',/  ∂¨∫øƒ∑Ó‡ﬁ€⁄ª•¶§¡˙√ß::")
    => '__foo-------------------------------------__))
