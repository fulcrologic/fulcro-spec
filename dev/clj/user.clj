(ns clj.user
  (:require [clojure.test :refer [is deftest run-tests testing]]
            smooth-test.async-spec
            smooth-test.specification-spec
            )
  )

(defn run-all-tests []
  (run-tests 'smooth-test.async-spec)
  (run-tests 'smooth-test.specification-spec)
  )