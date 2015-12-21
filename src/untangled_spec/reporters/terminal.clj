(ns untangled-spec.reporters.terminal
  (:require [clojure.test :as t :refer (are is deftest with-test run-tests testing testing-vars-str)]
            [clojure.stacktrace :as stack]
            [untangled-spec.reporters.impl.terminal :as impl]
            [colorize.core :as c]
            [clojure.data :refer [diff]]
            [io.aviso.exception :refer [format-exception *traditional*]])
  (:import clojure.lang.ExceptionInfo))

(defn color-str [status & strings]
  (let [status->color {:passed c/green
                       :failed c/red
                       :error  c/red}
        color-fn (or (status->color status) c/reset)]
    (apply color-fn strings)))

(defn space-level [level]
  (apply str (repeat (* 2 level) " ")))

(defn print-exception [e]
  (when (instance? java.lang.Exception e)
    (print (format-exception e {:frame-limit 10}))))

(defn get-exp-act [{:keys [extra] :as test-result}]
  (let [{:keys [arrow actual expected]} extra]
    (if (instance? Exception actual)
      (let [e actual]
        (print-exception e)
        [(str e) expected])
      (case (or arrow '=is=>)
        =>
        [actual expected]

        =is=>
        (let [{:keys [raw-actual actual expected]} test-result]
          (if (instance? Exception raw-actual)
            (let [e raw-actual]
              (print-exception e)
              [actual expected])
            [actual expected]))

        =fn=>
        [actual expected]

        =throws=>
        [(:actual test-result) expected]

        (throw (ex-info "invalid arrow" {:arrow arrow}))))))

(defn ?print-diff [act exp {:keys [raw-actual arrow]} print-fn]
  (when (and (= arrow '=>)
             (coll? exp)
             (not (map? exp))
             (not (instance? java.lang.Exception raw-actual)))
    ;clojure.data/diff does basic eq check for strings & maps -> redundant
    (let [[plus minus eq] (clojure.data/diff act exp)]
      (print-fn "    diff: -" minus)
      (print-fn "    diff: +" plus))))

(defn print-test-results [test-results print-fn]
  (->> test-results
       (remove #(= (:status %) :passed))
       (mapv (fn [{:keys [message where status raw-actual]
                   :as test-result}]
               (let [[act exp] (get-exp-act test-result)]
                 (print-fn)
                 (print-fn (if (= status :error)
                             "Error" "Failed") "in" where)
                 (when message (print-fn "ASSERTION:" message))
                 (print-fn "expected:" exp)
                 (print-fn "  actual:" act)
                 ;(?print-diff act exp test-result print-fn)
                 (print-fn))
               (when true
                 ;TODO: ^true -> :key in config?
                 (throw (ex-info "" {::stop? true})))))))

(defn print-test-item [test-item print-level]
  (t/with-test-out
    (println (space-level print-level)
             (color-str (:status test-item)
                        (:name test-item)))
    (print-test-results (:test-results test-item)
                        (partial println (space-level (inc print-level))))
    (->> (:test-items test-item)
         (mapv #(print-test-item % (inc print-level))))))

(defn print-namespace [make-tests-by-namespace]
  (t/with-test-out
    (println)
    (println (color-str (:status make-tests-by-namespace)
                        "Testing " (:name make-tests-by-namespace)))
    (->> (:test-items make-tests-by-namespace)
         (mapv #(print-test-item % 1)))))

(defn print-report-data
  "Prints the current report data from the report data state and applies colors based on test results"
  []
  (t/with-test-out
    (let [{:keys [namespaces tested passed failed error]} @impl/*test-state*]
      (try (->> namespaces
                (mapv print-namespace))
           (catch Exception e
             (when-not (->> e ex-data ::stop?)
               (print-exception e))))
      (println "\nRan" tested "tests containing"
               (+ passed failed error) "assertions.")
      (println failed "failures,"
               error "errors."))))

(defmulti ^:dynamic untangled-report :type)

(defmethod untangled-report :default [m])

(defmethod untangled-report :pass [m]
  (t/inc-report-counter :pass)
  (impl/pass))

(defmethod untangled-report :error [m]
  (t/inc-report-counter :error)
  (impl/error (-> m
                  (merge {:where (clojure.test/testing-vars-str m)})
                  (update :expected str
                          :actual   str))))

(defmethod untangled-report :fail [m]
  (t/inc-report-counter :fail)
  (impl/fail (-> m
                 (merge {:where (clojure.test/testing-vars-str m)})
                 (update :expected str
                         :actual   str))))

(defmethod untangled-report :begin-test-ns [m]
  (impl/begin-namespace (ns-name (:ns m))))

(defmethod untangled-report :end-test-ns [m]
  (impl/end-namespace))

(defmethod untangled-report :begin-specification [m]
  (impl/begin-specification (:string m)))

(defmethod untangled-report :end-specification [m]
  (impl/end-specification))

(defmethod untangled-report :begin-behavior [m]
  (impl/begin-behavior (:string m)))

(defmethod untangled-report :end-behavior [m]
  (impl/end-behavior))

(defmethod untangled-report :begin-manual [m]
  (impl/begin-behavior (str (:string m) "(MANUAL)")))

(defmethod untangled-report :end-manual [m]
  (impl/end-behavior))

(defmethod untangled-report :begin-provided [m]
  (impl/begin-provided (:string m)))

(defmethod untangled-report :end-provided [m]
  (impl/end-provided))

(defmethod untangled-report :summary [m]
  (let [stats {:tested (:test m) :passed (:pass m)
               :failed (:fail m) :error (:error m)}]
    (impl/summary stats)
    (print-report-data)))

(defmacro with-untangled-output
  "Execute body with modified test reporting functions that produce
  outline output"
  [& body]
  `(binding [t/report untangled-report]
     ~@body))
