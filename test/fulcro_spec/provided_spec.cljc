(ns fulcro-spec.provided-spec
  (:require
    [clojure.spec.alpha :as s]
    [nubank.workspaces.core :refer [deftest]]
    [fulcro-spec.core #?(:clj :refer :cljs :refer-macros)
     [behavior provided assertions when-mocking provided! when-mocking!]]
    #?(:clj [fulcro-spec.impl.macros :as im])
    #?(:clj [fulcro-spec.provided :as p])
    [fulcro-spec.stub :as stub]
    [fulcro-spec.spec :as fss]
    [fulcro-spec.testing-helpers :as th])
  #?(:clj
     (:import clojure.lang.ExceptionInfo)))

#?(:clj
   (deftest parse-arrow-count
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
   (deftest parse-mock-triple
     (let [test-parse (comp (partial p/parse-mock-triple false)
                        (partial fss/conform! :fulcro-spec.provided/triple))]
       (let [result (test-parse '[(f a b) =2x=> (+ a b)])]
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
             (contains? result :mock-name) => true
             (:mock-name result) => 'f)))
       (let [result (test-parse '[(f 1 :b c) =2x=> (+ 1 c)])]
         (behavior "parses literals into :literals key"
           (assertions
             (contains? result :literals) => true
             (:literals result) => [1 :b ::stub/any]))
         (behavior "converts literals in the arglist into symbols"
           (assertions
             (->> (:stub-function result)
               second (every? symbol?))
             => true))))))

#?(:clj
   (deftest provided-macro
     (behavior "Outputs a syntax-quoted block"
       (let [expanded (p/provided* false "some string"
                        '[(f n) => (+ n 1)
                          (f n) =2x=> (* 3 n)
                          (under-test)])]
         (behavior "with a let of the scripted stubs"
           (let [let-defs (second (th/locate `let expanded))]
             (assertions
               (count let-defs) => 2)))
         (behavior "containing a script with the number proper steps"
           (let [script-steps (last (th/locate `stub/make-script expanded))]
             (assertions
               (vector? script-steps) => true
               (count script-steps) => 2
               (first (first script-steps)) => 'fulcro-spec.stub/make-step
               (first (second script-steps)) => 'fulcro-spec.stub/make-step)))
         (behavior "surrounds the assertions with a redef"
           (let [redef-block (th/locate `with-redefs expanded)]
             (assertions
               (first redef-block) => `with-redefs
               (vector? (second redef-block)) => true
               (th/locate 'under-test redef-block) => '(under-test))))
         (behavior "sends do-report when given a string"
           (assertions
             (take 2 (th/locate `im/with-reporting expanded))
             => `(im/with-reporting
                   {:type :provided :string "PROVIDED: some string"})))))

     (behavior "Can do mocking without output"
       (let [expanded    (p/provided* false :skip-output
                           '[(f n) => (+ n 1)
                             (f n) =2x=> (* 3 n)
                             (under-test)])
             redef-block (th/locate `with-redefs expanded)]
         (assertions
           (first redef-block) => `with-redefs
           (vector? (second redef-block)) => true
           (th/locate 'under-test expanded) => '(under-test)
           "no do-report pair"
           (count (remove nil? redef-block)) => 4)))))

(defn my-square [x] (* x x))

(defn my-varargs-sum [n & nums] (apply + n nums))

(deftest provided-test
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
    (let [detector (volatile! false)]
      (when-mocking
        (my-square n) =1x=> (+ n 5)
        (my-square n) => (+ n 7)

        (+ 1 2) (+ 1 2) (+ 1 2) (+ 1 2)
        (* 3 3) (* 3 3) (* 3 3) (* 3 3)
        (my-square 2)
        (my-square 2)
        (vreset! detector true))
      (assertions
        @detector => true))))

(defn function-with-spec
  ([a])
  ([a b])
  ([a b & more]))

(s/fdef function-with-spec
  :args (s/alt
          :unary (s/cat :a int?)
          :binary (s/cat :a int? :b int?)
          :many (s/cat :a int? :b int? :c (s/* int?)))
  :ret int?)

(defn call-to-test [a]
  (function-with-spec a))

(deftest provided!-test
  (behavior "Force mocks to conform to the specs of the original function"
    (provided! "The stubbed function returns an ok value"
      (function-with-spec n) => 22

      (assertions
        "Throws an exception if the arguments to the mock do not conform"
        (call-to-test "a") =throws=> {:regex #"was sent argument"}
        "Allows the body to run if args and return are ok"
        (call-to-test 42) => 22))
    (provided! "The stubbed function returns something incorrect for the spec"
      (function-with-spec n) => "crap"

      (assertions
        "Throws an exception about the stub's return value"
        (call-to-test 42) =throws=> {:regex #"returned a value"}))))
