(ns ^:figwheel-no-load fulcro-spec.impl.selectors
  (:require
    #?(:clj [clojure.tools.namespace.repl :as tools-ns-repl])))

#?(:clj (tools-ns-repl/disable-reload!))

(defonce selectors (atom {:current nil :default nil}))
