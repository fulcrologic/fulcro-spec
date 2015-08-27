(ns ^:figwheel-always cljs.user
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)])
  (:require [smooth-test.async :as async]
            [smooth-test.runner.browser :as b]
            cljs.pprint
            [cemerick.cljs.test :as t]))

(enable-console-print!)

(defn on-load []
  (t/test-ns 'smooth-test.async-spec)
  )

