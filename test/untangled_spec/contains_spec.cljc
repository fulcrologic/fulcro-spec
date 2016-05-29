(ns untangled-spec.contains-spec
  (:require [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior assertions]]
            [untangled-spec.contains :refer [*contains?]]))

(specification "*contains?"
  (behavior "can check that a string"
    (behavior "contains a string"
      (assertions
        (str "somestring") =fn=> (*contains? "mestri")))
    (behavior "contains a regex"
      (assertions
        (str "abrpij") =fn=> (*contains? #"pij$"))))
  (behavior "can check that a map"
    (behavior "contains a subset"
      (assertions
        {:k1 :v1 :k2 :v2} =fn=> (*contains? {:k1 :v1})))
    (behavior "contains certain keys"
      (assertions
        {:k1 :v1 :k2 :v2} =fn=> (*contains? [:k1 :k2] :keys)))
    (behavior "contains certain values"
      (assertions
        {:k1 :v1 :k2 :v2} =fn=> (*contains? [:v1 :v2] :vals))))
  (behavior "can check that a set"
    (behavior "contains 1+ in another seq"
      (assertions
        #{8 6 3 2} =fn=> (*contains? #{0 1 2})))
    (behavior "contains all in another seq"
      (assertions
        #{4 9 5 0} =fn=> (*contains? [0 4 5]))))
  (behavior "can check that a list/vector"
    (behavior "contains 1+ in another seq"
      (assertions
        [2 4 7] =fn=> (*contains? #{0 1 2})))
    (behavior "contains a subseq"
      (assertions
        [1 2]  =fn=> (*contains? [1 2])
        [1 2]  =fn=> (comp not (*contains? [2 1]))
        [1 21] =fn=> (comp not (*contains? [1 2]))
        [12 1] =fn=> (comp not (*contains? [1 2]))
        ))
    (behavior "contains a subseq with gaps"
      #_(assertions
          [3 7 0 1] =fn=> (*contains? [3 1] :gaps)
          [3 7 0 1] =fn=> (*contains? [3 7] :gaps)
          [3 7 0 1] =fn=> (comp not (*contains? [1 0] :gaps))
          ))
    (behavior "contains a subseq in any order"
      #_(assertions
          [34 7 1 87] =fn=> (*contains? [87 1] :any-order)
          ))
    (behavior "contains a subseq with gaps & in any order"
      #_(assertions
          [98 32 78 16] =fn=> (*contains? [16 98] :both)
          ))))
