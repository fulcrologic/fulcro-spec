(ns untangled-spec.spec-renderer
  (:require
    [untangled-spec.suite :as suite]))

(enable-console-print!)

(defonce renderer (suite/test-renderer))
