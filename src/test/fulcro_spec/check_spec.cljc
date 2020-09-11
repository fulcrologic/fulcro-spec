(ns fulcro-spec.check-spec
  (:require
    [clojure.test :as t]
    [fulcro-spec.check :as check :refer [checker]]
    [fulcro-spec.core :refer [assertions]]))

(;do
 comment
  (t/deftest checkers-smoke-test
    (let [x-double? (checker [actual]
                      (when-not (double? (get-in actual [:x]))
                        {:actual actual
                         :expected `double?
                         :message "x was not a double"}))
          failing-checker (checker [actual]
                            (vector
                              (let [v (get-in actual [:FAKE/int])]
                                (when-not (int? v)
                                  {:actual v, :expected `int?
                                   :message ":FAKE/int was not an int"}))
                              (let [v (get-in actual [:FAKE/string])]
                                (when-not (string? v)
                                  {:actual v, :expected `string?
                                   :message ":FAKE/string was not an string"}))))
          data {:a 1 :b {:c 2 :d 3}}]
      (assertions
        123  =check=> (check/equals?* 456)
        222  =check=> (check/is?* odd?)
        {}   =check=> (check/valid?* number?)
        data =check=> x-double?
        data =check=> failing-checker
        data =check=> (check/all*
                        (check/embeds?* {:a "A"})
                        (check/embeds?* {:b {:c "C"}}))
        data =check=> (check/embeds?*
                        {:a (check/is?* even?)
                         :b {:c (check/equals?* 7)}})))))
