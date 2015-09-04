(ns smooth-test.provided-spec
  #?(:clj
     (:require [smooth-test.core :as c :refer [specification behavior provided with-timeline event tick assertions]]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report)]
               [smooth-test.provided :as p]
               ))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             [smooth-test.provided :as p]
             [smooth-test.core :refer [specification behavior provided with-timeline async tick assertions]]
             ))
  #?(:cljs (:require [cljs.test :refer [do-report]]
             )
     )
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )

#?(:clj
   (specification "parse-arrow-count"
                  (behavior "requires the arrow start with an ="
                            (is (thrown? AssertionError (p/parse-arrow-count '->)))
                            )
                  (behavior "requires the arrow end with =>"
                            (is (thrown? AssertionError (p/parse-arrow-count '=2x>)))
                            )
                  (behavior "derives a :many count for general arrows"
                            (assertions
                              (p/parse-arrow-count '=>) => :many
                              )
                            )
                  (behavior "throws an assertion error if count is zero"
                            (is (thrown? AssertionError (p/parse-arrow-count '=0x=>)))
                            )
                  (behavior "derives a numeric count for numbered arrows"
                            (assertions
                              (p/parse-arrow-count '=1x=>) => 1
                              (p/parse-arrow-count '=7=>) => 7
                              (p/parse-arrow-count '=234x=>) -> 234
                              )
                            )
                  ))

#?(:clj
   (specification "parse-mock-triple"
                  (let [result (p/parse-mock-triple ['(f a b) '=2x=> '(+ a b)])]
                    (behavior "includes a call count"
                              (assertions
                                (contains? result :ntimes) => true
                                (:ntimes result) => 2
                                )
                              )
                    (behavior "includes a stubbing function"
                              (assertions
                                          (contains? result :stub-function) => true
                                          (:stub-function result) => '(clojure.core/fn [a b] (+ a b))
                                          )
                              )
                    (behavior "includes the symbol to mock"
                              (assertions
                                (contains? result :symbol-to-mock) => true
                                (:symbol-to-mock result) => 'f
                                )
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
                    (behavior "creates a vector of triples (each in a vector)"
                              (is (vector? scripts))
                              (is (every? vector? scripts))
                              )


                    (behavior "nested vectors each contain the symbol to mock as their first element"
                              (assertions
                                (first (first scripts)) => 'a
                                (first (second scripts)) => 'b
                                )
                              )

                    (behavior "nested vectors each contain a unique script symbol in their second element"
                              (assertions
                                (count (reduce (fn [acc ele] (conj acc (second ele))) #{} scripts)) => 2
                                ))

                    (behavior "nested vectors' last member is a syntax-quoted call to make-script"
                              (assertions
                                (last (first scripts)) =>
                                '(smooth-test.stub/make-script "a" [(smooth-test.stub/make-step (fn [] 22) 2) (smooth-test.stub/make-step (fn [] 32) 1)])
                                (last (second scripts)) =>
                                '(smooth-test.stub/make-script "b" [(smooth-test.stub/make-step (fn [] 42) 1)])
                                ))
                    ))
   )

#?(:clj
   (specification "provided-macro"
                  (behavior "Outputs a syntax-quoted block"
                            (let [expanded (p/provided-fn '(f n) '=> '(+ n 1) '(f n) '=2x=> '(* 3 n) '(is (= 1 2)))
                                  let-defs (second expanded)
                                  script-steps (last (second let-defs))
                                  redef-block (last expanded)
                                  ]
                              (behavior "with a let of the scripted stubs"
                                        (assertions (first expanded) => 'clojure.core/let
                                                    (count let-defs) => 2
                                                    (vector? let-defs) => true
                                                    )
                                        )
                              (behavior "containing a script with the number proper steps"
                                        (assertions
                                          (count script-steps) => 2)
                                        )
                              (behavior "that surrounds the final assertions with a redef"
                                        (assertions
                                          (first redef-block) => 'clojure.core/with-redefs
                                          (last redef-block) => '(is (= 1 2))
                                          )
                                        )
                              )
                            )
                  )
   )

(defn my-square [x] (* x x))

(specification "provided-macro"
               (behavior "actually causes stubbing to work"
                         (provided "that functions are mocked the correct number of times, with the correct output values."
                                   (my-square n) =1x=> 1
                                   (my-square n) =2x=> 1
                                   (assertions
                                     (+ (my-square 7) (my-square 7) (my-square 7)) => 3
                                     )
                                   )
                         (provided "a mock for 2 calls"
                                   (my-square n) =1x=> (+ n 5)
                                   (my-square n) =1x=> (+ n 7)
                                   (behavior "throws an exception if the mock is called 3 times"
                                             (is (thrown? ExceptionInfo
                                                          (+ (my-square 1) (my-square 1) (my-square 1))))
                                             )
                                   )


                         (provided "a mock for 3 calls with 2 different return values"
                                   (my-square n) =1x=> (+ n 5)
                                   (my-square n) =2x=> (+ n 7)
                                   (behavior "all 3 mocked calls return the mocked values"
                                             (assertions
                                               (+ (my-square 1) (my-square 1) (my-square 1)) => 22
                                               ))
                                   )
                         )

               (behavior "allows any number of trailing forms"
                         (let [detector (atom false)]
                           (provided "mocks that are not used"
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
                                     (my-square 2)
                                     (my-square 2)
                                     (reset! detector true)
                                     )
                           (is (= true @detector))
                           ))
               )


;(deftest Boo
;  (testing "when I like pizza"
;    (testing "and somebody is home"
;      (are [x y] (= 0 (mod x y))
;                 6 3
;                 4 2
;                 11 3
;                 )
;
;      (is (contains? thing :k))
;      (click-button :label "Today" (rendering boo))
;      (tick 100)
;
;      (provided "clause"
;                (js/setTimeout cb & tm) =1x=> (async tm (cb [1 2 3])) ; exactly once
;                (js/setTimeout cb & tm) =1x=> (do
;                                                (verify-arg tm 100)
;                                                (.log js/console "OK")
;                                                (async tm (cb [1 2 3]))
;                                                )
;                (something) =2x=> 400
;                (js/setTimeout cb _) => (async 100 (cb [1 2 3])) ; many
;
;                (behavior ""
;                          a => 2
;                          b => 2
;                          c => 2
;                          d => 2
;                          )
;
;                )
;      )
;
;    (testing "clause"
;      (let [testing-async-queue (make-async-queue)
;            stub1 (fn [cb & tm] (schedule-item testing-async-queue tm (fn [] (cb [1 2 3]))))    ; arity 2
;            stub2 (fn [cb & tm] (do
;                                  ; FIXME: Exception types for js?
;                                  (if-not (is (= 100 tm)) (throw (Exception. "Argument check failed")))
;                                  (.log js/console "OK")
;                                  (schedule-item testing-async-queue tm (fn [] (cb [1 2 3])))
;                                  ))
;            stub3 (fn [] 400)
;            stub4 (fn [cb _] (schedule-item testing-async-queue 100 (fn [] (cb [1 2 3]))))
;            settimeout-script-atom (make-script "js/setTimeout"
;                                          [
;                                                   (make-step stub1 1)
;                                                   (make-step stub2 1)
;                                                   (make-step stub4 2)
;                                                   ])
;            something-script-atom (atom
;                                    {:f      "something"
;                                     :script [
;                                              {:times 2 :stub stub2 :argcnt 0 :ncalled 0}
;                                              ]})
;            ]
;        (with-redefs [js/setTimeout (scripted-stub settimeout-script-atom)
;                        something (scripted-stub something-script-atom)
;
;                        ...
;
;                        (verify-scripted-atom settimeout-script-atom)
;                        (verify-scripted-atom something-script-atom)
;
;          )
;
;        )
;      )
;
;    (with-bindings [queue (make-queue)
;                    captured-thing (atom nil)
;                    ajax-get (async-stub 200 anything (call-with [2 3 4]) anything)]
;      (click-button :label "Go" frag)
;
;      (async/schedule-event queue 200 stub)
;      (async/advance-clock queue 300)
;
;      )
;    (a/assertions "ass stuff")