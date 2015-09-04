(ns clj.user
  (:require [clojure.test :refer [is deftest run-tests testing]]
            [smooth-test.report :as report]
            smooth-test.provided-spec
            )
  )

(defn run-all-tests []
  (report/with-smooth-output
      (run-tests 'smooth-test.provided-spec)
   )
  )


