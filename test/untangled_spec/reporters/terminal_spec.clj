(ns untangled-spec.reporters.terminal-spec
  (:require [untangled-spec.reporters.terminal :refer [get-exp-act print-exception]]
            [untangled-spec.assertions :refer [triple->assertion]]
            [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report assert-expr)]
            [untangled-spec.core :refer [specification component behavior provided assertions]])
  (:import clojure.lang.ExceptionInfo))

(specification "untangled-spec.terminal-spec"
  (component "get-exp-act"
    (behavior "with no :extra field, just returns [actual expected]"
      (assertions
        (get-exp-act {:actual 0 :expected 1}) => [0 1]))
    (let [test-case (fn [x] (-> x triple->assertion eval get-exp-act))]

      (provided "with :extra, ie: from triple->assertion"
        (clojure.test/do-report x) => x
        (print-exception _) => _

        (component "=>"
          (behavior "basic"
            (is (= [5 3] (test-case '(5 => 3)))))
          (behavior "complex"
            (is (= [5 3] (test-case '((+ 3 2) => (+ 2 1))))))
          (behavior "deals with unexpected exceptions"
            (is (= ["clojure.lang.ExceptionInfo:  {}" 3]
                   (test-case '((throw (ex-info "" {})) => 3))))))

        (component "=fn=>"
          (behavior "basic"
            (is (= [5 'even?] (test-case '(5 =fn=> even?)))))
          (behavior "lambda"
            (is (re-find #"even\?"
                         (->> '(5 =fn=> #(even? %))
                              test-case second str))))
          (behavior "complex"
            (is (= [7 '(fn [x] (even? x))]
                   (test-case '((+ 5 2) =fn=> (fn [x] (even? x)))))))
          (behavior "deals with unexpected exceptions"
            (is (= ["clojure.lang.ExceptionInfo:  {}" 'even?]
                   (test-case '((throw (ex-info "" {})) =fn=> even?))))))

        (component "=throws=>"
          (behavior "simple"
            (let [[act exp] (test-case '((throw (ex-info "foo" {}))
                                         =throws=> (clojure.lang.ExceptionInfo #"foo")))]
              (is (= 'clojure.lang.ExceptionInfo (first exp)))
              (is (= "foo" (str (second exp))))
              (is (= true act)))
            (let [[act exp] (test-case '((throw (ex-info "foo" {}))
                                         =throws=> (clojure.lang.ExceptionInfo #"asdf")))]
              (is (= 'clojure.lang.ExceptionInfo (first exp)))
              (is (= "asdf" (str (second exp))))
              (is (= "clojure.lang.ExceptionInfo: exception's message did not match regex {:regex #\"asdf\", :msg \"foo\"}" (str act))))
            (let [[act exp] (test-case '((+ 5 2) =throws=> (clojure.lang.ExceptionInfo)))]
              (is (= 'clojure.lang.ExceptionInfo (first exp)))
              (is (re-find #"Expected an '.*' to be thrown!" (.getMessage act)))
              (is (= {:type :untangled-spec.assertions/internal}
                     (ex-data act))))))))))
