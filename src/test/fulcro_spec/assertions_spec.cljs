(ns fulcro-spec.assertions-spec
  (:require
    [nubank.workspaces.core :refer [deftest]]
    [fulcro-spec.core :refer [specification assertions]]))

(deftest assert-test
  (assertions
    "throws arrow can catch"
    (assert false "foobar") =throws=> (js/Error #"ooba")
    "throws arrow can catch js/Objects"
    (throw #js {}) =throws=> (js/Object)))
