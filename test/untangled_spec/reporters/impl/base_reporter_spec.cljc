(ns untangled-spec.reporters.impl.base-reporter-spec
  (:require [untangled-spec.reporters.impl.base-reporter :as base]
            [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided
              component assertions]]))

(def nf untangled-spec.reporters.impl.diff/nf)

(specification "base-reporter-spec"
  (component "diff"
    (assertions
      "returns nil for non collections"
      (base/diff 0 1) => nil)
    (assertions "returns 'simple' diff if different types"
      (base/diff [{}] [#{}])
      => [[0 [:+ {} :- #{}]]]
      (base/diff {0 {}} {0 #{}})
      => [[0 [:+ {} :- #{}]]]
      (base/diff #{0 1} [0 1])
      => [[[:+ #{0 1} :- [0 1]]]])
    (behavior "strings"
      (assertions
        "simple diff"
        (base/diff "asdf" "qwer")
        => [[[:+ "asdf" :- "qwer"]]]
        ))
    (behavior "maps"
      (assertions
        "returns a list of paths to the diffs"
        (base/diff {0 1 1 1} {0 2})
        => [[0 [:+ 1 :- 2]]
            [1 [:+ 1 :- nf]]]

        "if actual has extra keys than expected, the diff will specify a removal"
        (base/diff {} {0 0})
        => [[0 [:+ nf :- 0]]]
        (base/diff {0 {}} {0 {1 1}})
        => [[0 1 [:+ nf :- 1]]]

        "nil values wont show up as removals"
        (base/diff {0 nil} {0 0})
        => [[0 [:+ nil :- 0]]]

        "recursive cases work too!"
        (base/diff {0 {1 2}} {0 {1 1}})
        => [[0 1 [:+ 2 :- 1]]]

        "handles coll as keys"
        (base/diff {0 {1 {[2 3] 3
                          {4 4} 4}}}
                   {0 {1 {[2 3] :q}}})
        => [[0 1 [2 3] [:+ 3 :- :q]]
            [0 1 {4 4} [:+ 4 :- nf]]]))
    (behavior "sequences"
      (assertions
        "both empty"
        (base/diff [] []) => []

        "same length"
        (base/diff [0] [1])
        => [[0 [:+ 0 :- 1]]]
        (base/diff [0 1] [1 2])
        => [[0 [:+ 0 :- 1]]
            [1 [:+ 1 :- 2]]]
        (base/diff [0 1] [1 1])
        => [[0 [:+ 0 :- 1]]]

        "diff lengths"
        (base/diff [] [1])
        => [[0 [:+ nf :- 1]]]
        (base/diff [2] [])
        => [[0 [:+ 2 :- nf]]]
        (base/diff [] [0 1])
        => [[0 [:+ nf :- 0]]
            [1 [:+ nf :- 1]]]

        "diff is after some equals"
        (base/diff [0 1 2 3]
                   [0 1 2 :three])
        => [[3 [:+ 3 :- :three]]]

        "recursive!"
        (base/diff [{0 0}] [{0 1}])
        => [[0 0 [:+ 0 :- 1]]]
        (base/diff [{:questions {:ui/curr 1}}]
                   [{:questions {}}])
        => [[0 :questions :ui/curr [:+ 1 :- nf]]]
        ))))
