(ns fulcro-spec.reporters.terminal-spec
  (:require
    [clojure.string :as str]
    [clojure.test :as t :refer [deftest is]]
    [fulcro-spec.core :as core]
    [fulcro-spec.reporter :as base]
    [fulcro-spec.reporters.terminal :as term]))

(defn test! [test-fn]
  (t/report {:type :begin-test-ns :ns *ns*})
  (try (test-fn)
       (catch Throwable e
         (t/report
           {:type     :error, :message "Uncaught exception, not in assertion."
            :expected nil, :actual e})))
  (t/report {:type :end-test-ns :ns *ns*}))

(def __SHADOW_TEST_PLEASE_IGNORE__
  (fn []
    (with-redefs [base/inc-report-counter (fn [_])]
      (let [reporter (base/make-test-reporter)]
        (base/with-fulcro-reporting {:test/reporter reporter}
          (fn [& _])
          (test!
            (fn []
              (core/behavior "terminal reporting prints good results"
                (core/assertions
                  "for => arrow"
                  nil => 1
                  2 => nil
                  (some-> nil :fn) => 3
                  4 => (some-> nil :x)
                  "for =fn=> arrow"
                  even? =fn=> 5
                  6 =fn=> odd?
                  7 =fn=> (some-> nil :fn))))))
        reporter))))

(deftest terminal-reporter
  (binding [term/*config* {:quick-fail? false}]
    (core/behavior "actual and expected are printed correctly"
      (let [reporter (__SHADOW_TEST_PLEASE_IGNORE__)]
        (core/behavior "test harness sanity check"
          (is (= (type (base/map->TestReporter {}))
                (type reporter)))
          (is (= [:id :name :test-items :status]
                (keys (first (:namespaces (base/get-test-report reporter)))))))
        (let [report-ns (first (:namespaces (base/get-test-report reporter)))
              report-behavior (first (:test-items report-ns))
              [eq-arrow-tests fn-arrow-tests] (:test-items report-behavior)
              find-actual-str #(second (re-find #"\s+Actual: ([^\n]*)" %))
              find-expected-str #(second (re-find #"\s+Expected: ([^\n]*)" %))]
          (is (= "terminal reporting prints good results" (:name report-behavior)))
          (is (= "for => arrow" (:name eq-arrow-tests)))
          (is (= [["nil" "1"], ["2" "nil"], ["nil" "3"], ["4" "nil"]]
                (map #((juxt find-actual-str find-expected-str)
                       (with-out-str (term/print-test-result % println 0)))
                  (:test-results eq-arrow-tests))))
          (let [[test-5, test-6, test-7]
                (map #(with-out-str (term/print-test-result % println 0))
                  (:test-results fn-arrow-tests))]
            (is (str/starts-with? (find-actual-str test-5) "java.lang.ClassCastException"))
            (is (= "(exec 5 even?)" (find-expected-str test-5)))
            (is (= "6" (find-actual-str test-6)))
            (is (re-find #"odd_QMARK" (find-expected-str test-6)))
            (is (re-find #"java.lang.NullPointerException" (find-actual-str test-7)))
            (is (= "(exec (some-> nil :fn) 7)" (find-expected-str test-7)))))))))
