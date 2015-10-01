(ns clj.user
  (:require [clojure.test :refer [is deftest run-tests testing]]
            [smooth-spec.report :as report]
            smooth-spec.provided-spec
            smooth-spec.async-spec
            smooth-spec.stub-spec
            smooth-spec.timeline-spec
            )
  )

(defn run-all-tests []
  (report/with-smooth-output
    (run-tests 'smooth-spec.provided-spec
               'smooth-spec.async-spec
               'smooth-spec.stub-spec
               'smooth-spec.timeline-spec)
    )
  )


