(ns smooth-test.provided-spec
  #?(:clj
     (:require [smooth-test.provided :as p]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               [smooth-test.provided :as p]))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             [smooth-test.provided :as p]
             ))
  #?(:cljs (:require [cljs.test :as t]
             ))
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )

#?(:clj
   (deftest parse-arrow-count-spec
     (testing "requires the arrow start with an ="
       (is (thrown? AssertionError (p/parse-arrow-count '->)))
       )
     (testing "requires the arrow end with =>"
       (is (thrown? AssertionError (p/parse-arrow-count '=2x>)))
       )
     (testing "derives a :many count for general arrows"
       (is (= :many (p/parse-arrow-count '=>)))
       )
     (testing "throws an assertion error if count is zero"
       (is (thrown? AssertionError (p/parse-arrow-count '=0x=>)))
       )
     (testing "derives a numeric count for numbered arrows"
       (is (= 1 (p/parse-arrow-count '=1x=>)))
       (is (= 7 (p/parse-arrow-count '=7=>)))
       (is (= 234 (p/parse-arrow-count '=234x=>)))
       )
     ))

#?(:clj
   (deftest parse-mock-triple-spec
     (let [result (p/parse-mock-triple ['(f a b) '=2x=> '(+ a b)])]
       (testing "includes a call count"
         (is (contains? result :ntimes))
         (is (= 2 (:ntimes result)))
         )
       (testing "includes a stubbing function"
         (is (contains? result :stub-function))
         (is (= '(clojure.core/fn [a b] (+ a b)) (:stub-function result)))
         )
       (testing "includes the symbol to mock"
         (is (contains? result :symbol-to-mock))
         (is (= 'f (:symbol-to-mock result)))
         )
       )
     )
   )

#?(:clj
   (deftest convert-groups-to-symbolic-triples-spec
     (let [grouped-data {'a [
                             {:ntimes 2 :symbol-to-mock 'a :stub-function '(fn [] 22)}
                             {:ntimes 1 :symbol-to-mock 'a :stub-function '(fn [] 32)}
                             ]
                         'b [
                             {:ntimes 1 :symbol-to-mock 'b :stub-function '(fn [] 42)}
                             ]}
           scripts (p/convert-groups-to-symbolic-triples grouped-data)
           ]
       (testing "creates a vector of triples (each in a vector)"
         (is (vector? scripts))
         (is (every? vector? scripts))
         )
       (testing "nested vectors each contain the symbol to mock as their first element"
         (is (= 'a (first (first scripts))))
         (is (= 'b (first (second scripts))))
         )
       (testing "nested vectors each contain a unique script symbol in their second element"
         (is (= 2 (count (reduce (fn [acc ele] (conj acc (second ele))) #{} scripts))))
         )
       (testing "nested vectors' last member is a syntax-quoted call to make-script"
         (is (=
               '(smooth-test.stub/make-script "a" [(smooth-test.stub/make-step (fn [] 22) 2) (smooth-test.stub/make-step (fn [] 32) 1)])
               (last (first scripts))))
         (is (=
               '(smooth-test.stub/make-script "b" [(smooth-test.stub/make-step (fn [] 42) 1)])
               (last (second scripts))))
         )
       )))

#?(:clj
   (deftest provided-macro-spec
     (testing "Outputs a syntax-quoted block"
       (let [expanded (p/provided-fn '(f n) '=> '(+ n 1) '(f n) '=2x=> '(* 3 n) '(is (= 1 2)))
             let-defs (second expanded)
             script-steps (last (second let-defs))
             redef-block (last expanded)
             ]
         (testing "with a let of the scripted stubs"
           (is (= 'clojure.core/let (first expanded)))
           (is (= 2 (count let-defs)))
           (is (vector? let-defs))
           )
         (testing "containing a script with the number proper steps"
           (is (= 2 (count script-steps)))
           )
         (testing "that surrounds the final assertions with a redef"
           (is (= 'clojure.core/with-redefs (first redef-block)))
           (is (= '(is (= 1 2)) (last redef-block)))
           )
         )
       )
     )
   )

(defn my-square [x] (* x x))

(deftest provided-spec
  (testing "provided macro actually causes stubbing to work"
    (testing "can mock a function the correct number of times, with the correct output values."
      (p/provided
        (my-square n) =1x=> 1
        (my-square n) =2x=> 1
        (is (= 3
               (+ (my-square 7) (my-square 7) (my-square 7))))))
    (testing "allows the use of the arguments passed to the mock"
      (p/provided
        (my-square n) =1x=> (+ n 5)
        (my-square n) =2x=> (+ n 7)
        (is (= 22                                           ;6 + 8 + 8
               (+ (my-square 1) (my-square 1) (my-square 1))))))
    (testing "throws an exception if the mock is called too much"
      (p/provided 
                  (my-square n) =1x=> (+ n 5)
                  (my-square n) =1x=> (+ n 7)

                  (is (thrown? ExceptionInfo
                               (+ (my-square 1) (my-square 1) (my-square 1))))
                  )))
  (testing "allows any number of trailing forms"
    (let [detector (atom false)]
      (p/provided
        (my-square n) =1x=> (+ n 5)
        (my-square n) =2x=> (+ n 7)
        
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

