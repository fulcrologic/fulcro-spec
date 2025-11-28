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
  ;; Clojure 1.11: :actual/:expected contain values (e.g., 2)
  ;; Clojure 1.12+: :actual/:expected contain forms (e.g., (= 2 2))
  (let [addition-report-1-11 {:type :pass :expected 2 :actual 2 :message "msg: addition" :assert-type 'eq}
        even-report-1-11     {:type :pass :expected '(even? 64) :actual '(even? 64) :message "msg: even 64"}
        actual               (into []
                               (comp
                                 (filter #(-> % :type (#{:fail :error :pass})))
                                 (map #(select-keys % [:type :expected :actual :assert-type :message])))
                               @reports)
        ;; Helper to check if report matches expected structure (works for both 1.11 and 1.12)
        matches-report?      (fn [report msg]
                               (and (= :pass (:type report))
                                 (= msg (:message report))))]
    (assertions
      "capturing clojure.test reports for comparison"
      ;; Accept either 1.11 format (exact match) or 1.12 format (structure match)
      (or (= actual [addition-report-1-11 even-report-1-11])
        (and (= 2 (count actual))
          (matches-report? (first actual) "msg: addition")
          (matches-report? (second actual) "msg: even 64")))
      => true

      "checking generated assertions against clojure.test reports"
      ;; Check structure rather than exact match for 1.11/1.12 compatibility
      (let [result (eval (ae/eq-assert-expr "msg: addition" (list 2 (+ 1 1))))]
        (and (= :pass (:type result))
          (= "msg: addition" (:message result))))
      => true

      ((juxt ::ae/actual ::ae/expected) (eval (ae/eq-assert-expr "msg: addition" (list 2 (+ 1 1)))))
      => [nil nil]

      (let [result (eval (ae/fn-assert-expr "msg: even 64" (list 'even? 64)))]
        (and (= :pass (:type result))
          (= "msg: even 64" (:message result))
          (= 64 (::ae/actual result))
          (= even? (::ae/expected result))))
      => true)))

(comment
  (require 'fulcro-spec.reporters.terminal)
  (binding [clojure.test/report fulcro-spec.reporters.terminal/fulcro-report]
    (clojure.test/run-tests)))
