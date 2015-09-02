(ns smooth-test.provided-spec
  #?(:clj
     (:require [smooth-test.core :as c :refer [specification behavior provided with-timeline event tick assertions]]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               [smooth-test.provided :as p]
               ))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             [smooth-test.provided :as p]
             ))
  #?(:cljs (:require [cljs.test :as t]
             [smooth-test.core :as c :include-macros true]
             )
     )
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )

#?(:clj
   (specification "parse-arrow-count"
                  (assertions "requires the arrow start with an ="
                            (is (thrown? AssertionError (p/parse-arrow-count '->)))
                            )
                  (assertions "requires the arrow end with =>"
                            (is (thrown? AssertionError (p/parse-arrow-count '=2x>)))
                            )
                  (assertions "derives a :many count for general arrows"
                              (p/parse-arrow-count '=>) => :many
                            )
                  (assertions "throws an assertion error if count is zero"
                            (is (thrown? AssertionError (p/parse-arrow-count '=0x=>)))
                            )
                  (assertions "derives a numeric count for numbered arrows"
                            (p/parse-arrow-count '=1x=>) => 1
                            (p/parse-arrow-count '=7=>) => 7
                            (p/parse-arrow-count '=234x=>) -> 234
                            )
                  ))

#?(:clj
   (specification "parse-mock-triple"
                  (let [result (p/parse-mock-triple ['(f a b) '=2x=> '(+ a b)])]
                    (assertions "includes a call count"
                                (contains? result :ntimes) => true
                                (:ntimes result) => 2
                                )
                    (assertions "includes a stubbing function"
                                (contains? result :stub-function) => true
                                (:stub-function result) => '(clojure.core/fn [a b] (+ a b))
                              )
                    (assertions "includes the symbol to mock"
                                (contains? result :symbol-to-mock) => true
                                (:symbol-to-mock result) => 'f
                              )
                    )
                  )
   )

#?(:clj
   (specification "convert-groups-to-symbolic-triples"
                  (let [grouped-data {'a [
                                          {:ntimes 2 :symbol-to-mock 'a :stub-function '(fn [] 22)}
                                          {:ntimes 1 :symbol-to-mock 'a :stub-function '(fn [] 32)}
                                          ]
                                      'b [
                                          {:ntimes 1 :symbol-to-mock 'b :stub-function '(fn [] 42)}
                                          ]}
                        scripts (p/convert-groups-to-symbolic-triples grouped-data)
                        ]
                    (assertions "creates a vector of triples (each in a vector)"
                                (is (vector? scripts))
                                (is (every? vector? scripts))
                                )

                    (assertions "nested vectors each contain the symbol to mock as their first element"
                                (first (first scripts)) => 'a
                                (first (second scripts)) => 'b
                                )

                    (assertions "nested vectors each contain a unique script symbol in their second element"
                                (count (reduce (fn [acc ele] (conj acc (second ele))) #{} scripts)) => 2
                                )

                    (assertions "nested vectors' last member is a syntax-quoted call to make-script"
                                (last (first scripts)) =>
                                '(smooth-test.stub/make-script "a" [(smooth-test.stub/make-step (fn [] 22) 2) (smooth-test.stub/make-step (fn [] 32) 1)])
                                (last (second scripts)) =>
                                '(smooth-test.stub/make-script "b" [(smooth-test.stub/make-step (fn [] 42) 1)])
                                )
                    )))

#?(:clj
   (specification "provided-macro"
                  (assertions "Outputs a syntax-quoted block"
                            (let [expanded (p/provided-fn '(f n) '=> '(+ n 1) '(f n) '=2x=> '(* 3 n) '(is (= 1 2)))
                                  let-defs (second expanded)
                                  script-steps (last (second let-defs))
                                  redef-block (last expanded)
                                  ]
                              (assertions "with a let of the scripted stubs"
                                          (first expanded) => 'clojure.core/let
                                          (count let-defs) => 2
                                          (vector? let-defs) => true
                                          )
                              (assertions "containing a script with the number proper steps"
                                          (count script-steps) => 2
                                          )
                              (assertions "that surrounds the final assertions with a redef"
                                          (first redef-block) => 'clojure.core/with-redefs
                                          (last redef-block) => '(is (= 1 2))
                                          )
                              )
                            )
                  )
   )

(defn my-square [x] (* x x))

(specification "provided-macro"
               (assertions "actually causes stubbing to work"
                         (provided
                           (my-square n) =1x=> 1
                           (my-square n) =2x=> 1
                           (assertions "can mock a function the correct number of times, with the correct output values."
                                       (+ (my-square 7) (my-square 7) (my-square 7)) => 3

                                       )
                           )
                         (provided
                           (my-square n) =1x=> (+ n 5)
                           (my-square n) =2x=> (+ n 7)
                           (assertions "allows the use of the arguments passed to the mock"
                                       (+ (my-square 1) (my-square 1) (my-square 1)) => 22
                                       )
                           )
                         (provided
                           (my-square n) =1x=> (+ n 5)
                           (my-square n) =1x=> (+ n 7)
                           (assertions "throws an exception if the mock is called too much"
                                     (is (thrown? ExceptionInfo
                                                  (+ (my-square 1) (my-square 1) (my-square 1))))
                                     )
                           )
                         )
               (assertions "allows any number of trailing forms"
                         (let [detector (atom false)]
                           (provided
                             (my-square n) =1x=> (+ n 5)
                             (my-square n) => (+ n 7)

                             (+ 1 2)
                             (+ 1 2)
                             (+ 1 2)
                             (+ 1 2)
                             (* 3 3)
                             (* 3 3)
                             (* 3 3)
                             (* 3 3)
                             (reset! detector true)
                             )
                           (is (= true @detector))
                           ))
               )
