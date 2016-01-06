(ns untangled-spec.reporters.terminal-spec
  (:require [untangled-spec.core
             :refer [specification component behavior provided assertions]]
            [untangled-spec.reporters.terminal :as rt]
            [clojure.test :as t]))

(defn stop? [e] (-> e ex-data :untangled-spec.reporters.terminal/stop?))

(specification "untangled-spec.reporters.terminal-spec"
  (component "print-test-result"
    (provided "prints machine readable expected and actual"
      (pr-str "exp") =1x=> "exp"
      (pr-str "act") =1x=> "act"
      (assertions
        (rt/print-test-result {:actual "act" :expected "exp"} (constantly nil))
        =throws=> (clojure.lang.ExceptionInfo #"" stop?)))
    (provided "if (isa? actual Throwable) & (= status :error), it should print-throwable"
      (rt/print-throwable _) => _
      (let [e (ex-info "howdy" {})]
        (assertions
          (rt/print-test-result {:status :error :actual e} (constantly nil))
          =throws=> (clojure.lang.ExceptionInfo #"" stop?))))))
