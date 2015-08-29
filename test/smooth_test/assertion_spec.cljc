(ns smooth-test.assertion-spec
  #?(:clj
     (:require [smooth-test.assertion :as a]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               [smooth-test.async :as async]
               [smooth-test.async :as async]))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             [smooth-test.assertion :as a]
             ))
  #?(:cljs (:require [cljs.test :as t]
             [smooth-test.assertion :as a]
             )))

(deftest value-detection-works-on-expected-value-types
  (are [v] (a/is-value? v)
           1
           "hello"
           #{1 2 3}
           {:k 2}
           [1 2 3]
           )
  )

(deftest value-detection-ignores-plain-lists
  (is (not (a/is-value? '(1 2 3)))))

(deftest function-detection-detects-lambdas
  (is (a/is-function? (fn [a] a)))
  )

(defn a-function [] 1)

(deftest function-detection-detects-symbolic-functions
  (is (a/is-function? a-function))
  )

#?(:clj
   (deftest capture-invocation-wraps-invocation-in-try-catch
     (is (= (a/captured-invocation '(f)) '(try (f) (catch e {:smooth-test.assertion/failed e}))))
     ))

#?(:clj
   (deftest lambda-forms-can-be-detected-as-functions
     (is (a/is-lambda-form? '#(print %)))
     (is (a/is-lambda-form? '(fn [n] print n)))
     ))

(deftest Boo
  (testing "when I like pizza"
    (testing "and somebody is home"
      (are [x y] (= 0 (mod x y))
                 6 3
                 4 2
                 11 3
                 )
      
      (is (contains? thing :k))
      (click-button :label "Today" (rendering boo))
      (tick 100)
      
      ;(with-bindings [queue (make-queue)
                      ;captured-thing (atom nil)
                      ;ajax-get (async-stub 200 anything (call-with [2 3 4]) anything)]
        ;(click-button :label "Go" frag)
        ;
        ;(async/schedule-event queue 200 stub )
        ;(async/advance-clock queue 300)
        ;
        ;)
      (a/assertions "ass stuff"
                    (-> @app-state :button :label) => "Hello"
                    (contains? thing :k) => true
                    (contains? thing :k) =throws=> (Exception. #"msg regexx")
                    (+ 1 23 2) => 24
                    4 => 4
                    ))
    )
  )

(assert "3 is odd" (odd? 3))
