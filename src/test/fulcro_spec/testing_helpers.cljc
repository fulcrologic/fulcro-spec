(ns fulcro-spec.testing-helpers
  (:require
    [clojure.walk :as w]))

(defn locate
  "Locates and returns (sym ...) in haystack.
   For use in tests that assert against code blocks for assertions
   about content that are decoupled from shape and/or location."
  [sym haystack]
  (let [needle (volatile! nil)]
    (w/prewalk
      #(do (when (and (not @needle)
                   (seq? %) (= sym (first %)))
             (vreset! needle %))
         %)
      haystack)
    @needle))
