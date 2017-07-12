(ns fulcro-spec.spec-renderer
  (:require
    [fulcro-spec.suite :as suite]))

(enable-console-print!)

(defonce renderer (suite/test-renderer))
