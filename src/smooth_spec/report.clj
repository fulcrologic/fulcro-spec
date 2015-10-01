(ns smooth-spec.report
  (:require [clojure.test :as t :refer (are is deftest with-test run-tests testing testing-vars-str)]
            [clojure.stacktrace :as stack]
            [smooth-spec.report-data :as rd]
            [colorize.core :as c]
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

(defn print-test-results [test-results print-level]
  (loop [filtered-test-results (filter #(not (= (:status %) :passed)) test-results)]
    (when (not (empty? filtered-test-results))
      (let [test-result (first filtered-test-results)]
        (println)
        (println (space-level print-level) (if (= (:status test-result) :error) "Error" "Failed") " in" (:where test-result))
        (when-let [message (:message test-result)] (println (space-level print-level) message))
        (println (space-level print-level) "expected:" (pr-str (:expected test-result)))
        (println (space-level print-level) "  actual:" (pr-str (:actual test-result)))
        (println)
        (recur (rest filtered-test-results))))))

(defn print-test-item [test-item print-level]
  (t/with-test-out
    (println (space-level print-level) (color-str (:status test-item) (:name test-item)))
    (print-test-results (:test-results test-item) (inc print-level))
    (loop [test-items (:test-items test-item)]
      (when (not (empty? test-items))
        (print-test-item (first test-items) (inc print-level))
        (recur (rest test-items))))))

(defn print-namespace [make-tests-by-namespace]
  (t/with-test-out
    (println)
    (println (color-str (:status make-tests-by-namespace) "Testing " (:name make-tests-by-namespace)))
    (loop [test-items (:test-items make-tests-by-namespace)]
      (when (not (empty? test-items))
        (do (print-test-item (first test-items) 1)
            (recur (rest test-items)))))))

(defn print-report-data
  "Prints the current report data from the report data state and applies colors based on test results"
  []
  (t/with-test-out
    (let [report-data @rd/*test-state*
          namespaces (get report-data :namespaces)]
      (loop [ns namespaces]
        (when (not (empty? ns))
          (print-namespace (first ns))
          (recur (rest ns))
          )
        )
      (println "\nRan" (:tested report-data) "tests containing"
               (+ (:passed report-data) (:failed report-data) (:error report-data)) "assertions.")
      (println (:failed report-data) "failures," (:error report-data) "errors.")
      ))
  )

(defmulti ^:dynamic smooth-report :type)

(defmethod smooth-report :default [m]
  )

(defmethod smooth-report :pass [m]
    (t/inc-report-counter :pass)
    (rd/pass)
    )

(defmethod smooth-report :error [m]
    (t/inc-report-counter :error)
    (let [detail {:where    (clojure.test/testing-vars-str m)
                  :message  (:message m)
                  :expected (str (:expected m))
                  :actual   (str (:actual m))}]
      (rd/error detail)
      )
    )

(defmethod smooth-report :fail [m]
    (t/inc-report-counter :fail)
    (let [detail {:where    (clojure.test/testing-vars-str m)
                  :message  (:message m)
                  :expected (str (:expected m))
                  :actual   (str (:actual m))}]
      (rd/fail detail)))

(defmethod smooth-report :begin-test-ns [m]
  (rd/begin-namespace (ns-name (:ns m)))
  )

(defmethod smooth-report :end-test-ns [m]
  (rd/end-namespace)
  )


(defmethod smooth-report :begin-specification [m]
  (rd/begin-specification (:string m)))


(defmethod smooth-report :end-specification [m]
  (rd/end-specification)
  )

(defmethod smooth-report :begin-behavior [m]
  (rd/begin-behavior (:string m))
  )

(defmethod smooth-report :end-behavior [m]
  (rd/end-behavior)
  )

(defmethod smooth-report :begin-provided [m]
  (rd/begin-provided (:string m))
  )

(defmethod smooth-report :end-provided [m]
  (rd/end-provided)
  )

(defmethod smooth-report :summary [m]
  (let [stats {:tested (:test m) :passed (:pass m) :failed (:fail m) :error (:error m)}]
    (rd/summary stats)
    (print-report-data)
    )
  )

(defmacro with-smooth-output
  "Execute body with modified test reporting functions that produce
  smooth output"
  [& body]
  `(binding [t/report smooth-report]
     ~@body))