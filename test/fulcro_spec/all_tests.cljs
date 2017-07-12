(ns fulcro-spec.all-tests
  (:require
    fulcro-spec.tests-to-run ;; ensures tests are loaded so doo can find them
    [doo.runner :refer-macros [doo-all-tests]]))

;;entry point for CI cljs tests, see github.com/bensu/doo
(doo-all-tests #"fulcro-spec\..*-spec")
