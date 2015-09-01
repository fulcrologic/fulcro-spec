(ns clj.user
  (:require [clojure.test :refer [is deftest run-tests testing]]
            smooth-test.async-spec
            )
  )

(defn run-all-tests []
  (run-tests 'smooth-test.async-spec)
  )