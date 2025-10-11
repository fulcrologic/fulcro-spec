(ns fulcro-spec.assertions-spec
  (:require
    [clojure.test :as t :refer [deftest is]]
    [fulcro-spec.assertions :as ae]
    [fulcro-spec.core
     :refer [assertions behavior]]
    [fulcro-spec.spec :as fss])
  (:import (clojure.lang ExceptionInfo)))

(defn check-assertion [expected]
  (fn [actual]
    (and
      (->> actual first (= 'clojure.test/is))
      (->> actual second (= expected)))))

(defn test-triple->assertion [form]
  (ae/triple->assertion false (fss/conform! ::ae/triple form)))

(deftest triple->assertion-test
  (behavior "checks equality with the => arrow"
    (assertions
      (test-triple->assertion '(left => right))
      =fn=> (check-assertion '(= right left))))
  (behavior "verifies actual with the =fn=> function"
    (assertions
      (test-triple->assertion '(left =fn=> right))
      =fn=> (check-assertion '(exec right left))))
  (behavior "any other arrow, throws an ex-info"
    (assertions
      (test-triple->assertion '(left =bad-arrow=> right))
      =throws=> ExceptionInfo)))

(deftest throws-assertion-arrow
  (behavior "catches AssertionErrors"
    (let [f (fn [x] {:pre [(even? x)]} (inc x))]
      (is (thrown? AssertionError (f 1)))
      (is (= 3 (f 2)))
      (assertions
        (f 1) =throws=> #"even\? x"
        (f 6) => 7
        (f 2) => 3))))

(deftest fix-conform-for-issue-31
  (assertions
    (mapv (juxt :behavior (comp count :triples))
      (ae/fix-conform
        (fss/conform! ::ae/assertions
          '("foo" 1 => 2 "bar" 3 => 4, 5 => 6 "qux" 7 => 8, 9 => 10))))
    => '[["foo" 1] ["bar" 2] ["qux" 2]]))

(def reports (atom []))
(defn recording-reporter [t]
  (swap! reports conj t))
(binding [t/report recording-reporter]
  (t/deftest deftest-var
    (t/testing "testing: string"
      (t/is (= 2 (+ 1 1)) "msg: addition")
      (t/is (even? 64) "msg: even 64")))
  (deftest-var))

(deftest fix-assertions-reporting-for-issue-13
  (let [addition-report {:type :pass :expected 2 :actual 2 :message "msg: addition" :assert-type 'eq}
        even-report {:type :pass :expected '(even? 64) :actual (list even? 64) :message "msg: even 64"}]
    (assertions
      "capturing clojure.test reports for comparison"
      (into []
        (comp
          (filter #(-> % :type (#{:fail :error :pass})))
          (map #(select-keys % [:type :expected :actual :assert-type :message])))
        @reports)
      => [addition-report even-report]

      "checking generated assertions against clojure.test reports"
      (eval (ae/eq-assert-expr "msg: addition" (list 2 (+ 1 1))))
      => addition-report

      ((juxt ::ae/actual ::ae/expected) (eval (ae/eq-assert-expr "msg: addition" (list 2 (+ 1 1)))))
      => [nil nil]

      (eval (ae/fn-assert-expr "msg: even 64" (list 'even? 64)))
      => (merge even-report
           {::ae/actual 64 ::ae/expected even? :assert-type 'exec}))))

(comment
  (require 'fulcro-spec.reporters.terminal)
  (binding [clojure.test/report fulcro-spec.reporters.terminal/fulcro-report]
    (clojure.test/run-tests)))
