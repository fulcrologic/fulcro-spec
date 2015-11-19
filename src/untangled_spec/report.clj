(ns untangled-spec.report
  (:require [clojure.test :as t :refer (are is deftest with-test run-tests testing testing-vars-str)]
            [clojure.stacktrace :as stack]
            [untangled-spec.report-data :as rd]
            [colorize.core :as c]
            [clojure.data :refer [diff]]
            [io.aviso.exception :as purdy :refer [format-exception *traditional*]]
            )
  (:import clojure.lang.ExceptionInfo)
  )

(defn color-str [status & strings]
  (cond (= status :passed) (apply c/green strings)
        (= status :failed) (apply c/red strings)
        (= status :error) (apply c/red strings)
        :otherwise (apply c/reset strings)
        )
  )

(defn space-level [level]
  (apply str (repeat (* 2 level) " ")))

(defn get-exp-act [{:keys [extra] :as test-result}]
  (let [{:keys [arrow actual expected]} extra]
    (case arrow
      =>
      (if (instance? Exception actual)
        (let [e actual]
          (do (println (format-exception e {:frame-limit 10}))
              [(str e) expected]))
        [actual expected])

      =fn=>
      [actual expected]

      =throws=>
      [(:actual test-result) expected]

      (throw (ex-info "invalid arrow" {:arrow arrow})))))

(defn print-diff [act exp {:keys [raw-actual arrow]} print-fn]
  (when (and (= arrow '=>)
             (coll? exp)
             (not (map? exp))
             (not (instance? java.lang.Exception raw-actual)))
    ;clojure.data/diff does basic eq check for strings & maps -> redundant
    (let [[plus minus eq] (clojure.data/diff act exp)]
      (print-fn "    diff: -" minus)
      (print-fn "    diff: +" plus))))

(defn print-exception [e print-fn]
  (when (instance? java.lang.Exception e)
    (binding [*traditional* true]
      (print-fn "  actual:" (str e))
      (println)
      ;TODO: MAGIC NUMBER :frame-limit
      (println (format-exception e {:frame-limit 10})))))

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
                 (print-diff act exp test-result print-fn)
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
    (let [report-data @rd/*test-state*
          namespaces (get report-data :namespaces)]
      (try (->> namespaces
                (mapv #(print-namespace %)))
           (catch Exception e
             (when-not (->> e ex-data ::stop?)
               (print-exception e println))))
      (println "\nRan" (:tested report-data) "tests containing"
               (+ (:passed report-data)
                  (:failed report-data)
                  (:error report-data)) "assertions.")
      (println (:failed report-data) "failures,"
               (:error report-data) "errors."))))

(defmulti ^:dynamic untangled-report :type)

(defmethod untangled-report :default [m]
  )

(defmethod untangled-report :pass [m]
    (t/inc-report-counter :pass)
    (rd/pass)
    )

(defn read-arrow [m]
  (-> m :extra :arrow symbol))

(defmethod untangled-report :error [m]
  (t/inc-report-counter :error)
  (let [detail {:where      (clojure.test/testing-vars-str m)
                :message    (:message m)
                :expected   (str (:expected m))
                :actual     (str (:actual m))
                :raw-actual (:actual m)
                :extra      (:extra m)
                :arrow      (read-arrow m)}]
    (rd/error detail)))

(defmethod untangled-report :fail [m]
  (t/inc-report-counter :fail)
  (let [detail {:where      (clojure.test/testing-vars-str m)
                :message    (:message m)
                :expected   (str (:expected m))
                :actual     (str (:actual m))
                :raw-actual (:actual m)
                :extra      (:extra m)
                :arrow      (read-arrow m)}]
    (rd/fail detail)))

(defmethod untangled-report :begin-test-ns [m]
  (rd/begin-namespace (ns-name (:ns m)))
  )

(defmethod untangled-report :end-test-ns [m]
  (rd/end-namespace)
  )


(defmethod untangled-report :begin-specification [m]
  (rd/begin-specification (:string m)))


(defmethod untangled-report :end-specification [m]
  (rd/end-specification)
  )

(defmethod untangled-report :begin-behavior [m]
  (rd/begin-behavior (:string m))
  )

(defmethod untangled-report :end-behavior [m]
  (rd/end-behavior)
  )

(defmethod untangled-report :begin-manual [m]
  (rd/begin-behavior (str (:string m) "(MANUAL)"))
  )

(defmethod untangled-report :end-manual [m]
  (rd/end-behavior)
  )

(defmethod untangled-report :begin-provided [m]
  (rd/begin-provided (:string m))
  )

(defmethod untangled-report :end-provided [m]
  (rd/end-provided)
  )

(defmethod untangled-report :summary [m]
  (let [stats {:tested (:test m) :passed (:pass m)
               :failed (:fail m) :error (:error m)}]
    (rd/summary stats)
    (print-report-data)
    )
  )

(defmacro with-untangled-output
  "Execute body with modified test reporting functions that produce
   outline output"
  [& body]
  `(binding [t/report untangled-report]
     ~@body))
