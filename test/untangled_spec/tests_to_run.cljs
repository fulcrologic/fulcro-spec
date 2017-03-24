(ns untangled-spec.tests-to-run
  (:require
    untangled-spec.assertions-spec
    untangled-spec.async-spec
    untangled-spec.contains-spec
    untangled-spec.diff-spec
    untangled-spec.provided-spec
    untangled-spec.selectors-spec
    untangled-spec.stub-spec
    untangled-spec.timeline-spec))

;********************************************************************************
; IMPORTANT:
; For cljs tests to work in CI, we want to ensure the namespaces for all tests are included/required. By placing them
; here (and depending on them in user.cljs for dev), we ensure that the all-tests namespace (used by CI) loads
; everything as well.
;********************************************************************************
