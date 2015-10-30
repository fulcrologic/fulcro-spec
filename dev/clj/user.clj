(ns clj.user
  (:require [clojure.test :refer [is deftest run-tests testing]]
            [untangled-spec.report :as report]
            untangled-spec.provided-spec
            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.timeline-spec
            )
  )

(defn run-all-tests []
  (report/with-untangled-output
    (run-tests 'untangled-spec.provided-spec
               'untangled-spec.async-spec
               'untangled-spec.stub-spec
               'untangled-spec.timeline-spec)
    )
  )


