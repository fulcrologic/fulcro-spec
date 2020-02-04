(ns fulcro-spec.reporters.terminal-spec
  (:require
    [clojure.test :as t :refer [deftest testing is]]
    [clojure.string :as str]
    [fulcro-spec.core :refer [specification behavior assertions]]
    [fulcro-spec.reporter :as base]
    [fulcro-spec.reporters.terminal :as term]))

(defn test! [v]
  (t/report {:type :begin-test-ns :ns *ns*})
  (try (v)
    (catch Throwable e
      (t/report
        {:type :error, :message "Uncaught exception, not in assertion."
         :expected nil, :actual e})))
  (t/report {:type :end-test-ns :ns *ns*}))

(def __SHADOW_TEST_PLEASE_IGNORE__
  (fn []
    (let [reporter (base/make-test-reporter)]
      (base/with-fulcro-reporting {:test/reporter reporter}
        (fn [& _])
        (test!
          (fn []
            (behavior "terminal reporting prints good results"
              (assertions
                "for => arrow"
                nil => 1
                2 => nil
                (some-> nil :fn) => 3
                4 => (some-> nil :x)
                (some-> nil :fn) => nil
                "for =fn=> arrow"
                even? =fn=> 5
                6 =fn=> odd?
                7 =fn=> (some-> nil :fn))))))
      reporter)))

(term/merge-cfg! {:quick-fail? false})

(deftest terminal-reporter
  (testing "actual and expected are printed correctly"
    (let [reporter (__SHADOW_TEST_PLEASE_IGNORE__)]
      (testing "test harness sanity check"
        (is (= (type (base/map->TestReporter {}))
               (type reporter)))
        (is (= [:id :name :test-items :status]
               (keys (first (:namespaces (base/get-test-report reporter)))))))
      (let [report-ns (first (:namespaces (base/get-test-report reporter)))
            report-behavior (first (:test-items report-ns))
            [eq-arrow-tests fn-arrow-tests] (:test-items report-behavior)
            find-actual-str #(second (re-find #"\s+Actual:\t(.*)" %))
            find-expected-str #(second (re-find #"\s+Expected:\t(.*)" %))]
        (is (= 'fulcro-spec.reporters.terminal-spec (:name report-ns)))
        (is (= "terminal reporting prints good results" (:name report-behavior)))
        (is (= "for => arrow" (:name eq-arrow-tests)))
        (is (= [["nil" "1"], ["2" "nil"], ["nil" "3"], ["4" "nil"]]
               (map #((juxt find-actual-str find-expected-str)
                      (with-out-str (term/print-test-result % println 0)))
                 (:test-results eq-arrow-tests))))
        (is (= [["class java.lang.ClassCastException" "(exec 5 even?)"]
                , ["6" "#function[clojure.core/odd?]"]
                , ["class java.lang.NullPointerException" "(exec (some-> nil :fn) 7)"]]
               (map #((juxt find-actual-str find-expected-str)
                      (with-out-str (term/print-test-result % println 0)))
                 (:test-results fn-arrow-tests))))))))
