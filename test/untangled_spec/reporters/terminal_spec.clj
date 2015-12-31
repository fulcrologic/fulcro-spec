(ns untangled-spec.reporters.terminal-spec
  (:require [untangled-spec.assertions :refer [triple->assertion]]
            [clojure.test :as t :refer [is]]
            [untangled-spec.core :refer [specification component behavior provided assertions]])
  (:import clojure.lang.ExceptionInfo))

(defn get-exp-act [{exp :expected act :actual msg :message} & [msg?]]
  (if msg? [act exp msg] [act exp]))

(specification "untangled-spec.terminal-spec"
  (component "get-exp-act"
    (behavior "with no :extra field, just returns [actual expected]"
      (assertions
        (get-exp-act {:actual 0 :expected 1 :message "msg"} true) => [0 1 "msg"]))

    (let [test-case (fn [x & [msg?]]
                      (binding [t/report (fn [m] m)]
                        (as-> x x (triple->assertion false x) (eval x) (get-exp-act x msg?))))
          test-case-msg #(test-case % true)]
      (provided "with :extra, ie: from triple->assertion"
        ;(clojure.test/do-report x) => x
        ;TODO: FIX/TEST ME

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
                         (->> '(7 =fn=> #(even? %))
                              test-case second str))))
          (behavior "complex"
            (is (= [7 '(fn [x] (even? x))]
                   (test-case '((+ 5 2) =fn=> (fn [x] (even? x))))))))

        (component "=throws=>"
          (behavior "simple"
            (is (= ["foo" "asdf" "exception's message did not match regex"]
                   (test-case-msg '((throw (ex-info "foo" {}))
                                    =throws=> (clojure.lang.ExceptionInfo #"asdf")))))
            (is (= ["it to throw" "Expected an 'clojure.lang.ExceptionInfo' to be thrown!"]
                   (-> '((+ 5 2) =throws=> (clojure.lang.ExceptionInfo #"asdf"))
                       test-case-msg rest)))))))))
