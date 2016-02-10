(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core :refer-macros [specification assertions]]))

(specification "assertions blocks work on cljs"
  (assertions
    "throws arrow can catch"
    (assert false "foobar") =throws=> (js/Error #"ooba")
    "throws arrow can catch js/Objects"
    (throw #js {}) =throws=> (js/Object)))
