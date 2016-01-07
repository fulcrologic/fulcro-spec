(ns untangled-spec.test-symbols
  (:require untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.report
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec))

;********************************************************************************
; IMPORTANT:
; For cljs tests to work in CI, we want to ensure the symbols for all test ns are included/required. By placing the
; symbols here (and depending on them in user.cljs for dev), we ensure that the all-tests namespace (used by CI) loads
; everything. It doesn't matter that all-tests does not use this, only that it requires it (loading is all it needs,
; and the requires in the file cause that.
;********************************************************************************

(def syms [
           'untangled-spec.async-spec
           'untangled-spec.stub-spec
           'untangled-spec.provided-spec
           'untangled-spec.report
           'untangled-spec.timeline-spec
           'untangled-spec.dom.events-spec
           'untangled-spec.dom.util-spec
           ])
