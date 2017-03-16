(ns untangled-spec.reporters.impl.diff-spec
  (:require
    [untangled-spec.reporters.impl.diff :as src
     :refer [nf diff diff-elem patch compress decompress]]
    [untangled-spec.core :as usc #?(:clj :refer :cljs :refer-macros)
     [specification behavior assertions]]))

(specification "the diff function"
  (assertions
    "returns {} for non collections"
    (diff 0 1) => {})

  (assertions "returns 'simple' diff if different types"
    (diff [{}] [#{}])
    => {[0] (diff-elem {} #{})}
    (diff {0 {}} {0 #{}})
    => {[0] (diff-elem {} #{})}
    (diff #{0 1} [0 1])
    => {[] (diff-elem #{0 1} [0 1])})

  (behavior "strings"
    (assertions
      "simple diff"
      (diff "asdf" "usef")
      => {[] (diff-elem "asdf" "usef")}))

  (behavior "maps"
    (assertions
      "returns a list of paths to the diffs"
      (diff {0 1 1 1} {0 2})
      => {[0] (diff-elem 1 2)
          [1] (diff-elem 1 nf)}

      "if actual has extra keys than expected, the diff will specify a removal"
      (diff {} {0 0})
      => {[0] (diff-elem nf 0)}
      (diff {0 {}} {0 {1 1}})
      => {[0 1] (diff-elem nf 1)}

      "nil values wont show up as removals"
      (diff {0 nil} {0 0})
      => {[0] (diff-elem nil 0)}

      "recursive cases work too!"
      (diff {0 {1 2}} {0 {1 1}})
      => {[0 1] (diff-elem 2 1)}

      "handles coll as keys"
      (diff {0 {1 {[2 3] 3
                   {4 4} 4}}}
            {0 {1 {[2 3] :q}}})
      => {[0 1 [2 3]] (diff-elem 3 :q)
          [0 1 {4 4}] (diff-elem 4 nf)}

      "differences accumulate over keys in map (github issue #3)"
      (diff {:current-tab :bookings
             :search-results {:global {:es-type "booking"}}}
            {:current-tab :messages
             :search-results {:global {:es-type "message"}}})
      =>  {[:search-results :global :es-type] [:+ "booking" :- "message"]
           [:current-tab] [:+ :bookings :- :messages]}

      "empty list as key"
      (diff {[] :foo} {[] :bar})
      => {[[]] (diff-elem :foo :bar)}

      "false as a key"
      (diff {[] {}} {[] {false 0}})
      => {[[] false] (diff-elem nf 0)}))

  (behavior "lists"
    (assertions
      "both empty"
      (diff '() '()) => {}

      "same length"
      (diff '(0) '(0)) => {}
      (diff '(0) '(1)) => {[0] (diff-elem 0 1)}
      (diff '(1 2 3) '(1 3 2)) => {[1] (diff-elem 2 3)
                                   [2] (diff-elem 3 2)}

      "diff lengths"
      (diff '(4 3 1) '(4)) => {[1] (diff-elem 3 nf)
                               [2] (diff-elem 1 nf)}
      (diff '() '(3 9)) => {[0] (diff-elem nf 3)
                            [1] (diff-elem nf 9)}

      "diff inside a nested list"
      (diff '[(app/exec {})] '[(app/do-thing {})])
      => {[0 0] (diff-elem 'app/exec 'app/do-thing)}))

  (behavior "lists & vectors"
    (assertions
      "works as though they are the same type"
      (diff '(0 1 3 4) [0 1 2 3])
      => {[2] (diff-elem 3 2)
          [3] (diff-elem 4 3)}))

  (behavior "vectors"
    (assertions
      "both empty"
      (diff [] []) => {}

      "same length"
      (diff [0] [1])
      => {[0] (diff-elem 0 1)}
      (diff [0 1] [1 2])
      => {[0] (diff-elem 0 1)
          [1] (diff-elem 1 2)}
      (diff [0 1] [1 1])
      => {[0] (diff-elem 0 1)}

      "diff lengths"
      (diff [] [1])
      => {[0] (diff-elem nf 1)}
      (diff [2] [])
      => {[0] (diff-elem 2 nf)}
      (diff [] [0 1])
      => {[0] (diff-elem nf 0)
          [1] (diff-elem nf 1)}

      "diff is after some equals"
      (diff [0 1 2 3]
            [0 1 2 :three])
      => {[3] (diff-elem 3 :three)}

      "recursive!"
      (diff [{0 0}] [{0 1}])
      => {[0 0] (diff-elem 0 1)}
      (diff [{:questions {:ui/curr 1}}]
            [{:questions {}}])
      => {[0 :questions :ui/curr] (diff-elem 1 nf)}))

  (behavior "sets"
    (assertions
      "both empty"
      (diff #{} #{}) => {[] []}

      "only one empty"
      (diff #{} #{2 3 4}) => {[] (diff-elem #{} #{2 3 4})}
      (diff #{2 3 4} #{}) => {[] (diff-elem #{2 3 4} #{})}

      "same length"
      (diff #{1}   #{2})   => {[] (diff-elem #{1}   #{2})}
      (diff #{1 3} #{2 3}) => {[] (diff-elem #{1}   #{2})}
      (diff #{1 5} #{2 4}) => {[] (diff-elem #{1 5} #{2 4})}

      "nested"
      (diff [{:foo [#{1 2}]}]
            [{:foo [#{3 2}]}])
      => {[0 :foo 0] (diff-elem #{1} #{3})}))

  (behavior "github issue #2"
    (assertions
      (diff {:current-tab [:messages :tab] :ui/loading-data false}
            {:current-tab [:settings :tab] :ui/loading-data false})
      => {[:current-tab 0] [:+ :messages :- :settings]})))

(specification "the patch function"
  (behavior "`patch` an object with a diff"
    (assertions
      (patch '() {[0] (diff-elem 'app/mut nf)})
      => '(app/mut)
      (patch '() {[1] (diff-elem 'app/mut nf)})
      =throws=> (#?(:cljs js/Error :clj IndexOutOfBoundsException)
                          #"(?i)Index.*Out.*Of.*Bounds")))
  (behavior "can patch 2 vectors, where actual is larger"
    (assertions
      (patch [] (diff [1 2] [])) => [1 2]))
  (behavior "gh-17, can patch a diff between a vector & a scalar"
    (assertions
      (patch [1 2] (diff 3 [1 2])) => {[] 3}))
  (behavior "gh-6, can patch a diff between 2 Cons or 2 IndexedSeq"
    (assertions
      (patch `[(1 2)] (diff `[(3 4)] `[(1 2)])) => `[(3 4)]
      (patch [1 2] (diff ((fn [& args] args) 3 4) [1 2])) => [3 4])))

(specification "[de]compression"
  (assertions "`compress` a sequence of states"
    (compress [{0 0} {0 1} {0 1 2 3}])
    => [{0 0}
        {[0] (diff-elem 1 0)}
        {[2] (diff-elem 3 nf)}]

    (compress [{0 0} {}])
    => [{0 0} {[0] (diff-elem nf 0)}])

  (assertions "`decompress` a compressed sequence"
    (decompress [{0 0}
                 {[0] (diff-elem 1 0)}
                 {[2] (diff-elem 3 nf)}])
    => [{0 0} {0 1} {0 1 2 3}]

    (decompress [{0 0} {[0] (diff-elem nf 0)}])
    => [{0 0} {}]))
