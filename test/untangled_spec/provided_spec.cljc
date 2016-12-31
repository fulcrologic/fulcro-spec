(ns untangled-spec.provided-spec
  (:require
    #?(:clj [clojure.test :refer [is]])
    #?(:cljs [cljs.test :refer-macros [is]])
    [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
     [specification behavior provided assertions when-mocking]]
    #?(:clj [untangled-spec.provided :as p])
    [untangled-spec.stub :as stub])
  #?(:clj
      (:import clojure.lang.ExceptionInfo)))

#?(:clj
   (specification "parse-arrow-count"
     (assertions
       "requires the arrow start with an ="
       (p/parse-arrow-count '->) =throws=> (AssertionError)
       "requires the arrow end with =>"
       (p/parse-arrow-count '=2x>) =throws=> (AssertionError)
       "derives a :many count for general arrows"
       (p/parse-arrow-count '=>) => :many
       "derives a numeric count for numbered arrows"
       (p/parse-arrow-count '=1x=>) => 1
       (p/parse-arrow-count '=7=>) => 7
       (p/parse-arrow-count '=234x=>) => 234)))

#?(:clj
   (specification "parse-mock-triple"
     (let [result (p/parse-mock-triple ['(f a b) '=2x=> '(+ a b)])]
       (behavior "includes a call count"
         (assertions
           (contains? result :ntimes) => true
           (:ntimes result) => 2))
       (behavior "includes a stubbing function"
         (assertions
           (contains? result :stub-function) => true
           (:stub-function result) => '(clojure.core/fn [a b] (+ a b))))
       (behavior "includes the symbol to mock"
         (assertions
           (contains? result :symbol-to-mock) => true
           (:symbol-to-mock result) => 'f)))
     (let [result (p/parse-mock-triple ['(f 1 :b c) '=2x=> '(+ 1 c)])]
       (behavior "parses literals into :literals key"
         (assertions
           (contains? result :literals) => true
           (:literals result) => [1 :b ::stub/any]))
       (behavior "converts literals in the arglist into symbols"
         (assertions
           (->> (:stub-function result)
             second (every? symbol?))
           => true)))))

#?(:clj
   (specification "convert-groups-to-symbolic-triples"
     (let [grouped-data {'a [{:ntimes 2 :symbol-to-mock
                              'a :stub-function '(fn [] 22)}
                             {:ntimes 1 :symbol-to-mock
                              'a :stub-function '(fn [] 32)}]
                         'b [{:ntimes 1 :symbol-to-mock
                              'b :stub-function '(fn [] 42)}]}
           scripts (p/convert-groups-to-symbolic-triples grouped-data)]

       (behavior "creates a vector of triples (each in a vector)"
         (is (vector? scripts))
         (is (every? vector? scripts)))

       (behavior "nested vectors each contain the symbol to mock as their first element"
         (assertions
           (first (first scripts)) => 'a
           (first (second scripts)) => 'b))

       (behavior "nested vectors each contain a unique script symbol in their second element"
         (assertions
           (count (reduce (fn [acc ele] (conj acc (second ele))) #{} scripts)) => 2))

       (behavior "nested vectors' last member is a syntax-quoted call to make-script"
         (assertions
           (last (first scripts)) =>
           '(untangled-spec.stub/make-script "a"
              [(untangled-spec.stub/make-step (fn [] 22) 2 nil)
               (untangled-spec.stub/make-step (fn [] 32) 1 nil)])
           (last (second scripts)) =>
           '(untangled-spec.stub/make-script "b"
              [(untangled-spec.stub/make-step (fn [] 42) 1 nil)]))))))

#?(:clj
   (specification "provided-macro"
     (behavior "Outputs a syntax-quoted block"
       (let [expanded (p/provided-fn false "some string"
                        '(f n) '=> '(+ n 1)
                        '(f n) '=2x=> '(* 3 n)
                        '(is (= 1 2)))
             let-defs (second expanded)
             make-script (second let-defs)
             script-steps (nth make-script 2)
             redef-block (last expanded)]
         (behavior "with a let of the scripted stubs"
           (assertions (first expanded) => 'clojure.core/let
             (count let-defs) => 2
             (vector? let-defs) => true))
         (behavior "containing a script with the number proper steps"
           (assertions
             (vector? script-steps) => true
             (count script-steps) => 2
             (first (first script-steps)) => 'untangled-spec.stub/make-step
             (first (second script-steps)) => 'untangled-spec.stub/make-step))
         (behavior "surrounds the assertions with a redef"
           (assertions
             (first redef-block) => 'clojure.core/with-redefs
             (vector? (second redef-block)) => true
             (nth redef-block 3) => '(is (= 1 2))))
         (behavior "sends do-report when given a string"
           (assertions
             (first (last redef-block)) => 'clojure.test/do-report
             (first (nth redef-block 2)) => 'clojure.test/do-report))))

     (behavior "Can do mocking without output"
       (let [expanded (p/provided-fn false :skip-output
                        '(f n) '=> '(+ n 1)
                        '(f n) '=2x=> '(* 3 n)
                        '(is (= 1 2)))
             redef-block (last expanded)]
         (assertions
           (first redef-block) => 'clojure.core/with-redefs
           (vector? (second redef-block)) => true
           (nth redef-block 2) => '(is (= 1 2))
           "no do-report pair"
           (count redef-block) => 4)))))

(defn my-square [x] (* x x))

(defn my-varargs-sum [n & nums] (apply + n nums))

(specification "provided and when-mocking macros"
  (behavior "cause stubbing to work"
    (provided "that functions are mocked the correct number of times, with the correct output values."
      (my-square n) =1x=> 1
      (my-square n) =2x=> 1
      (assertions
        (+ (my-square 7)
           (my-square 7)
           (my-square 7)) => 3))

    (behavior "throws an exception if the mock is called too many times"
      (when-mocking
        (my-square n) =1x=> (+ n 5)
        (my-square n) =1x=> (+ n 7)
        (assertions
          (+ (my-square 1)
             (my-square 1)
             (my-square 1))
          =throws=> (ExceptionInfo))))

    (provided "a mock for 3 calls with 2 different return values"
      (my-square n) =1x=> (+ n 5)
      (my-square n) =2x=> (+ n 7)
      (behavior "all 3 mocked calls return the mocked values"
        (assertions
          (+ (my-square 1)
             (my-square 1)
             (my-square 1)) => 22))))

  (provided "we can mock a var args function"
    (my-varargs-sum x y) =1x=> [x y]
    (my-varargs-sum x y z) => [x y z]
    (assertions
      (my-varargs-sum 1 2) => [1 2]
      (my-varargs-sum 1 2 3) => [1 2 3]))
  (provided "we can capture arguments variadically"
    (my-varargs-sum & y) => y
    (assertions
      (my-varargs-sum 1 2 3) => [1 2 3]))

  (provided "allow stubs to throw exceptions"
    (my-square n) => (throw (ex-info "throw!" {}))
    (assertions
      (my-square 1) =throws=> (ExceptionInfo)))

  (behavior "allows any number of trailing forms"
    (let [detector (atom false)]
      (when-mocking
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
        (reset! detector true))
      (is (= true @detector)))))
