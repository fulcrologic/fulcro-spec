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
