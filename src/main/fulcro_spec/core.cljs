(ns fulcro-spec.core
  (:require-macros
    [fulcro-spec.core])
  (:require
    [cljs.test :include-macros true]
    [fulcro-spec.assertions]
    [fulcro-spec.async]
    [fulcro-spec.stub]))

(declare => =1x=> =2x=> =3x=> =4x=> =throws=> =fn=>)
