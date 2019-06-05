(ns fulcro-spec.reporters.repl
  (:require
    [fulcro-spec.reporters.terminal :as term]
    [clojure.test :as t]))

(def ^:dynamic *selector-predicate* (constantly true))

(defn clj-test-all-vars [ns]
  (t/test-vars (filter *selector-predicate* (vals (ns-interns ns)))))

(defn clj-test-ns [ns]
  (binding [t/*report-counters* (ref t/*initial-report-counters*)]
    (let [ns-obj (the-ns ns)]
      (t/do-report {:type :begin-test-ns, :ns ns-obj})
      ;; If the namespace has a test-ns-hook function, call that:
      (if-let [v (find-var (symbol (str (ns-name ns-obj)) "test-ns-hook"))]
        ((var-get v))
        ;; Otherwise, just test every var in the namespace.
        (clj-test-all-vars ns-obj))
      (t/do-report {:type :end-test-ns, :ns ns-obj}))
    @t/*report-counters*))

(defn clj-run-tests []
  (let [summary (assoc (apply merge-with + (map clj-test-ns [*ns*])) :type :summary)]
    (t/do-report summary)
    summary))

(defn run-tests
  "Run the tests with the given `selector-predicate` (defaults to constantly true)."
  ([] (run-tests (constantly true)))
  ([selector-predicate]
   (let [start (System/currentTimeMillis)
         _     (binding [clojure.test/report  fulcro-spec.reporters.terminal/fulcro-report
                         *selector-predicate* selector-predicate]
                 (clj-run-tests))
         end   (System/currentTimeMillis)]
     (str "Elapsed time: " (- end start) " msecs"))))
