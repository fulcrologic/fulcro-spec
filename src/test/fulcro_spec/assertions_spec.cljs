(ns fulcro-spec.assertions-spec
  (:require
    [clojure.test :refer [deftest]]
    [fulcro-spec.core :refer [specification assertions]]))

(deftest assert-test
  (assertions
    "throws arrow can catch"
    (assert false "foobar") =throws=> #"ooba"
    "throws arrow can catch js/Objects"
    (throw #js {}) =throws=> :default))
