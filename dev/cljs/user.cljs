(ns ^:figwheel-always cljs.user
  (:require-macros [cljs.test
                    :refer (is run-tests testing)])
  (:require [untangled-spec.dom.suite :as ts :include-macros true]
            [untangled-spec.test-symbols :as s]
            untangled-spec.async-spec
            untangled-spec.stub-spec
            untangled-spec.provided-spec
            untangled-spec.report
            untangled-spec.timeline-spec
            untangled-spec.dom.events-spec
            untangled-spec.dom.util-spec
            [untangled-spec.runner.browser :as b]
            [cljs.test :as t]))

(enable-console-print!)

s/syms

(ts/test-suite spec-report
  ; FIXME: Use s/syms for the symbols
  'untangled-spec.async-spec
  'untangled-spec.stub-spec
  'untangled-spec.provided-spec
  'untangled-spec.timeline-spec
  'untangled-spec.dom.events-spec
  'untangled-spec.dom.util-spec)

(defn on-load [] (spec-report))

(spec-report)

