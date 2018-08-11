(ns ^:figwheel-no-load fulcro-spec.impl.selectors
  #?(:clj (:require [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce selectors (atom {:current nil :default nil}))
