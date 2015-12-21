(ns untangled-spec.reporters.impl.base-reporter
  #?(:cljs (:require [cljs-uuid-utils.core :as uuid])))


(defn make-testreport
  ([] (make-testreport []))
  ([initial-items]
   {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
    :summary       ""
    :namespaces    []
    #?@(:clj [:tested 0])
    :passed        0
    :failed        0
    :error         0}))

(defn make-testitem
  [name]
  {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
   :name         name
   :status       :pending
   :test-items   []
   :test-results []})

(defn make-manual [name] (make-testitem (str name " (MANUAL TEST)")))

(defn make-test-result
  [result result-detail]
  (merge result-detail
         {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
          :status result}))

(defn make-tests-by-namespace
  [name]
  {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
   :name       name
   :test-items []
   :status     :pending})

