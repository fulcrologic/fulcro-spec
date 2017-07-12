(ns fulcro-spec.tests-to-run
  (:require
    fulcro-spec.assertions-spec
    fulcro-spec.async-spec
    fulcro-spec.contains-spec
    fulcro-spec.diff-spec
    fulcro-spec.provided-spec
    fulcro-spec.selectors-spec
    fulcro-spec.stub-spec
    fulcro-spec.timeline-spec))

;********************************************************************************
; IMPORTANT:
; For cljs tests to work in CI, we want to ensure the namespaces for all tests are included/required. By placing them
; here (and depending on them in user.cljs for dev), we ensure that the all-tests namespace (used by CI) loads
; everything as well.
;********************************************************************************
