(ns untangled-spec.impl.runner
  (:require
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [untangled-spec.runner :as runner]))

(tools-ns-repl/disable-reload!)

(def runner (atom {}))
