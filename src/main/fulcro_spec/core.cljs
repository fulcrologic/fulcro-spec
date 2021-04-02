(ns fulcro-spec.core
  (:require-macros
    [fulcro-spec.core])
  (:require
    [cljs.test :include-macros true]
    [fulcro-spec.assertions]
    [fulcro-spec.async]
    [fulcro-spec.stub]
    [fulcro-spec.hooks :refer [hooks]]))

(declare => =1x=> =2x=> =3x=> =4x=> =throws=> =fn=>)

(defn set-hooks!
  "Call this to set the `:on-enter` and `:on-leave` hooks.
   Currently only `specification`, `behavior`, and `component` call these hooks.
   `:on-enter` and `:on-leave` will be called with a single argument,
   a map with the test or behavior string and its location (currently just line number).
   See the macro source code for exact and up to date details."
  [handlers]
  (reset! hooks handlers))
