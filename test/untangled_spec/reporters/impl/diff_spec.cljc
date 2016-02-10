(ns untangled-spec.reporters.impl.diff-spec
  (:require [untangled-spec.reporters.impl.diff :refer [nf diff]]
            [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior assertions]]))

(specification "the diff function"
  (assertions
    "returns nil for non collections"
    (diff 0 1) => nil)

  (assertions "returns 'simple' diff if different types"
    (diff [{}] [#{}])
    => [[0 [:+ {} :- #{}]]]
    (diff {0 {}} {0 #{}})
    => [[0 [:+ {} :- #{}]]]
    (diff #{0 1} [0 1])
    => [[[:+ #{0 1} :- [0 1]]]])

  (behavior "strings"
    (assertions
      "simple diff"
      (diff "asdf" "usef")
      => [[[:+ "asdf" :- "usef"]]]))

  (behavior "maps"
    (assertions
      "returns a list of paths to the diffs"
      (diff {0 1 1 1} {0 2})
      => [[0 [:+ 1 :- 2]]
          [1 [:+ 1 :- nf]]]

      "if actual has extra keys than expected, the diff will specify a removal"
      (diff {} {0 0})
      => [[0 [:+ nf :- 0]]]
      (diff {0 {}} {0 {1 1}})
      => [[0 1 [:+ nf :- 1]]]

      "nil values wont show up as removals"
      (diff {0 nil} {0 0})
      => [[0 [:+ nil :- 0]]]

      "recursive cases work too!"
      (diff {0 {1 2}} {0 {1 1}})
      => [[0 1 [:+ 2 :- 1]]]

      "handles coll as keys"
      (diff {0 {1 {[2 3] 3
                        {4 4} 4}}}
                 {0 {1 {[2 3] :q}}})
      => [[0 1 [2 3] [:+ 3 :- :q]]
          [0 1 {4 4} [:+ 4 :- nf]]]))

  (behavior "lists"
    (assertions
      "both empty"
      (diff '() '()) => []

      "same length"
      (diff '(0) '(0)) => []
      (diff '(0) '(1)) => [[0 [:+ 0 :- 1]]]
      (diff '(1 2 3) '(1 3 2)) => [[1 [:+ 2 :- 3]]
                                        [2 [:+ 3 :- 2]]]

      "diff lengths"
      (diff '(4 3 1) '(4)) => [[1 [:+ 3 :- nf]]
                                    [2 [:+ 1 :- nf]]]
      (diff '() '(3 9)) => [[0 [:+ nf :- 3]]
                                 [1 [:+ nf :- 9]]]))

  (behavior "lists & vectors"
    (assertions
      "works as though they are the same type"
      (diff '(0 1 3 4) [0 1 2 3])
      => [[2 [:+ 3 :- 2]]
          [3 [:+ 4 :- 3]]]))

  (behavior "vectors"
    (assertions
      "both empty"
      (diff [] []) => []

      "same length"
      (diff [0] [1])
      => [[0 [:+ 0 :- 1]]]
      (diff [0 1] [1 2])
      => [[0 [:+ 0 :- 1]]
          [1 [:+ 1 :- 2]]]
      (diff [0 1] [1 1])
      => [[0 [:+ 0 :- 1]]]

      "diff lengths"
      (diff [] [1])
      => [[0 [:+ nf :- 1]]]
      (diff [2] [])
      => [[0 [:+ 2 :- nf]]]
      (diff [] [0 1])
      => [[0 [:+ nf :- 0]]
          [1 [:+ nf :- 1]]]

      "diff is after some equals"
      (diff [0 1 2 3]
                 [0 1 2 :three])
      => [[3 [:+ 3 :- :three]]]

      "recursive!"
      (diff [{0 0}] [{0 1}])
      => [[0 0 [:+ 0 :- 1]]]
      (diff [{:questions {:ui/curr 1}}]
                 [{:questions {}}])
      => [[0 :questions :ui/curr [:+ 1 :- nf]]]))

  (behavior "sets"
    (assertions
      "both empty"
      (diff #{} #{}) => [[[]]]

      "only one empty"
      (diff #{} #{2 3 4}) => [[[:+ #{} :- #{2 3 4}]]]
      (diff #{2 3 4} #{}) => [[[:+ #{2 3 4} :- #{}]]]

      "same length"
      (diff #{1}   #{2})   => [[[:+ #{1}   :- #{2}]]]
      (diff #{1 3} #{2 3}) => [[[:+ #{1}   :- #{2}]]]
      (diff #{1 5} #{2 4}) => [[[:+ #{1 5} :- #{2 4}]]]

      "nested"
      (diff [{:foo [#{1 2}]}]
                 [{:foo [#{3 2}]}])
      => [[0 :foo 0 [:+ #{1} :- #{3}]]])))
