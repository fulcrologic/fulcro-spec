(ns smooth-test.provided-spec
  #?(:clj
     (:require [smooth-test.provided :as p]
               [smooth-test.behavior :as b]
               [smooth-test.specification :as s]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               [smooth-test.timeline :as timeline]
               ))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             [smooth-test.timeline :as timeline]
             [smooth-test.provided :as p]
             ))
  #?(:cljs (:require [cljs.test :as t]
             [smooth-test.async :as a :include-macros true]
             )
     )
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )

#?(:clj
   (s/specification "parse-arrow-count"
     (b/behavior "requires the arrow start with an ="
       (is (thrown? AssertionError (p/parse-arrow-count '->)))
       )
     (b/behavior "requires the arrow end with =>"
       (is (thrown? AssertionError (p/parse-arrow-count '=2x>)))
       )
     (b/behavior "derives a :many count for general arrows"
       (is (= :many (p/parse-arrow-count '=>)))
       )
     (b/behavior "throws an assertion error if count is zero"
       (is (thrown? AssertionError (p/parse-arrow-count '=0x=>)))
       )
     (b/behavior "derives a numeric count for numbered arrows"
       (is (= 1 (p/parse-arrow-count '=1x=>)))
       (is (= 7 (p/parse-arrow-count '=7=>)))
       (is (= 234 (p/parse-arrow-count '=234x=>)))
       )
     ))

#?(:clj
   (s/specification "parse-mock-triple"
     (let [result (p/parse-mock-triple ['(f a b) '=2x=> '(+ a b)])]
       (b/behavior "includes a call count"
         (is (contains? result :ntimes))
         (is (= 2 (:ntimes result)))
         )
       (b/behavior "includes a stubbing function"
         (is (contains? result :stub-function))
         (is (= '(clojure.core/fn [a b] (+ a b)) (:stub-function result)))
         )
       (b/behavior "includes the symbol to mock"
         (is (contains? result :symbol-to-mock))
         (is (= 'f (:symbol-to-mock result)))
         )
       )
     )
   )

#?(:clj
   (s/specification "convert-groups-to-symbolic-triples"
     (let [grouped-data {'a [
                             {:ntimes 2 :symbol-to-mock 'a :stub-function '(fn [] 22)}
                             {:ntimes 1 :symbol-to-mock 'a :stub-function '(fn [] 32)}
                             ]
                         'b [
                             {:ntimes 1 :symbol-to-mock 'b :stub-function '(fn [] 42)}
                             ]}
           scripts (p/convert-groups-to-symbolic-triples grouped-data)
           ]
       (b/behavior "creates a vector of triples (each in a vector)"
         (is (vector? scripts))
         (is (every? vector? scripts))
         )
       (b/behavior "nested vectors each contain the symbol to mock as their first element"
         (is (= 'a (first (first scripts))))
         (is (= 'b (first (second scripts))))
         )
       (b/behavior "nested vectors each contain a unique script symbol in their second element"
         (is (= 3 (count (reduce (fn [acc ele] (conj acc (second ele))) #{} scripts))))
         )
       (b/behavior "nested vectors' last member is a syntax-quoted call to make-script"
         (is (=
               '(smooth-test.stub/make-script "a" [(smooth-test.stub/make-step (fn [] 22) 2) (smooth-test.stub/make-step (fn [] 32) 1)])
               (last (first scripts))))
         (is (=
               '(smooth-test.stub/make-script "b" [(smooth-test.stub/make-step (fn [] 42) 1)])
               (last (second scripts))))
         )
       )))

#?(:clj
   (s/specification "provided-macro"
     (b/behavior "Outputs a syntax-quoted block"
       (let [expanded (p/provided-fn '(f n) '=> '(+ n 1) '(f n) '=2x=> '(* 3 n) '(is (= 1 2)))
             let-defs (second expanded)
             script-steps (last (second let-defs))
             redef-block (last expanded)
             ]
         (b/behavior "with a let of the scripted stubs"
           (is (= 'clojure.core/let (first expanded)))
           (is (= 2 (count let-defs)))
           (is (vector? let-defs))
           )
         (b/behavior "containing a script with the number proper steps"
           (is (= 2 (count script-steps)))
           )
         (b/behavior "that surrounds the final assertions with a redef"
           (is (= 'clojure.core/with-redefs (first redef-block)))
           (is (= '(is (= 1 2)) (last redef-block)))
           )
         )
       )
     )
   )

(defn my-square [x] (* x x))

(s/specification "provided-macro"
  (b/behavior "actually causes stubbing to work"
    (b/behavior "can mock a function the correct number of times, with the correct output values."
      (p/provided
        (my-square n) =1x=> 1
        (my-square n) =2x=> 1
        (is (= 3
               (+ (my-square 7) (my-square 7) (my-square 7))))))
    (b/behavior "allows the use of the arguments passed to the mock"
      (p/provided
        (my-square n) =1x=> (+ n 5)
        (my-square n) =2x=> (+ n 7)
        (is (= 22                                                               ;6 + 8 + 8
               (+ (my-square 1) (my-square 1) (my-square 1))))))
    (b/behavior "throws an exception if the mock is called too much"
      (p/provided
        (my-square n) =1x=> (+ n 5)
        (my-square n) =1x=> (+ n 7)

        (is (thrown? ExceptionInfo
                     (+ (my-square 1) (my-square 1) (my-square 1))))
        )))
  (b/behavior "allows any number of trailing forms"
    (let [detector (atom false)]
      (p/provided
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
