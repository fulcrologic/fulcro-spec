(ns untangled-spec.reporters.terminal-spec
  (:require [untangled-spec.core
             :refer [specification component behavior provided assertions]]
            [untangled-spec.reporters.terminal :as rt]
            [clojure.test :as t]))

(defn stop? [e] (-> e ex-data :untangled-spec.reporters.terminal/stop?))

(specification "print-test-result"
  (provided "if (isa? actual Throwable) & (= status :error), it should print-throwable"
    (rt/print-throwable _) => _
    (rt/env :quick-fail?) => true
    (let [e (ex-info "howdy" {})]
      (assertions
        (rt/print-test-result {:status :error :actual e} (constantly nil) 0)
        =throws=> (clojure.lang.ExceptionInfo #"" stop?)))))
(def big-thing (zipmap (range 5)
                       (repeat (zipmap (range 5) (range)))))
(specification "pretty-str"
  (behavior "put newlines in between lines"
    (assertions
      (rt/pretty-str big-thing 1) => (str "{0 {0 0, 1 1, 2 2, 3 3, 4 4},\n    "
                                          "1 {0 0, 1 1, 2 2, 3 3, 4 4},\n    "
                                          "2 {0 0, 1 1, 2 2, 3 3, 4 4},\n    "
                                          "3 {0 0, 1 1, 2 2, 3 3, 4 4},\n    "
                                          "4 {0 0, 1 1, 2 2, 3 3, 4 4}}"))))
