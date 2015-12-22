(ns untangled-spec.reporters.terminal-spec
  (:require [untangled-spec.reporters.terminal :refer [get-exp-act]]
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
        (component "=>"
          (behavior "basic"
            (is (= [5 3] (test-case '(5 => 3)))))
          (behavior "complex"
            (is (= [5 3] (test-case '((+ 3 2) => (+ 2 1)))))))

        (component "=fn=>"
          (behavior "basic"
            (is (= [5 'even?] (test-case '(5 =fn=> even?)))))
          (behavior "lambda"
            (is (re-find #"even\?"
                         (->> '(5 =fn=> #(even? %))
                              test-case second str))))
          (behavior "complex"
            (is (= [7 '(fn [x] (even? x))]
                   (test-case '((+ 5 2) =fn=> (fn [x] (even? x))))))))

        (component "=throws=>"
          (behavior "simple"
            ))))))
