(ns smooth-spec.report
    (:require [clojure.test :as t :refer (are is deftest with-test run-tests testing testing-vars-str)]
               [clojure.stacktrace :as stack]
               [smooth-spec.report-data :as rd]
               )
     (:import clojure.lang.ExceptionInfo)
  )

(def ^:dynamic *test-level* (atom 0))


(defn space-level []
  (apply str (repeat (* 3 @*test-level*) " "))
  )

(defn print-smooth-diagnostic
  "Prints a smooth diagnostic line.  data is a (possibly multi-line)
  string."
  [data]
  (doseq [line (.split ^String data "\n")]
    (println "#" line)))

(defn print-smooth-fail
  "Prints a smooth 'not ok' line.  msg is a string, with no line breaks"
  [msg]
  (println "not ok" msg))

(defmulti ^:dynamic smooth-report :type)

(defn print-diagnostics [data]
  (when (seq t/*testing-contexts*)
    (print-smooth-diagnostic (t/testing-contexts-str)))
  (when (:message data)
    (print-smooth-diagnostic (:message data)))
  (print-smooth-diagnostic (str "expected:" (pr-str (:expected data))))
  (if (= :pass (:type data))
    (print-smooth-diagnostic (str "  actual:" (pr-str (:actual data))))
    (do
      (print-smooth-diagnostic
        (str "  actual:"
             (with-out-str
               (if (instance? Throwable (:actual data))
                 (prn (.getMessage (:actual data))))))))))

(defmethod smooth-report :default [m]
  )

(defmethod smooth-report :pass [m]
  (t/with-test-out
    (t/inc-report-counter :pass)
    (rd/pass)
    ))

(defmethod smooth-report :error [m]
  (t/with-test-out
    (t/inc-report-counter :error)
    (print-smooth-fail (t/testing-vars-str m))
    (print-diagnostics m)
    (let [detail {:where    (clojure.test/testing-vars-str m)
                    :message  (:message m)
                    :expected (str (:expected m))
                    :actual   (str (:actual m))}]
      (rd/error detail)
      )
    ))

(defmethod smooth-report :fail [m]
  (t/with-test-out
    (t/inc-report-counter :fail)
    (println "\nFAIL in" (t/testing-vars-str m))
    (when-let [message (:message m)] (println message))
    (println "expected:" (pr-str (:expected m)))
    (println "  actual:" (pr-str (:actual m)))
    (println)
    (let [detail {:where    (clojure.test/testing-vars-str m)
                  :message  (:message m)
                  :expected (str (:expected m))
                  :actual   (str (:actual m))}]
      (rd/fail detail)
      )
    ))

(defmethod smooth-report :begin-test-ns [m]
  (t/with-test-out
    (println "\nTesting" (ns-name (:ns m))))
  (rd/begin-namespace (ns-name (:ns m)))
  )

(defmethod smooth-report :end-test-ns [m]
  (rd/end-namespace)
  )


(defmethod smooth-report :begin-specification [m]
  (t/with-test-out
    (reset! *test-level* 0)
    (println (space-level) (:string m)))
  (rd/begin-specification (:string m)))


(defmethod smooth-report :end-specification [m]
  (t/with-test-out
    (println)
    (reset! *test-level* 0)
    (rd/end-specification)
    )
  )

(defmethod smooth-report :begin-behavior [m]
  (t/with-test-out
    (swap! *test-level* inc)
    (println (space-level) (:string m)))
    (rd/begin-behavior (:string m))
  )

(defmethod smooth-report :end-behavior [m]
  (t/with-test-out
    (swap! *test-level* dec)
    )
  (rd/end-behavior)
  )

(defmethod smooth-report :begin-provided [m]
  (t/with-test-out
    (swap! *test-level* inc)
    (println (space-level) (:string m)))
  (rd/begin-provided (:string m))
  )

(defmethod smooth-report :end-provided [m]
  (t/with-test-out
    (reset! *test-level* 0)
    )
  (rd/end-provided)
  )

(defmethod smooth-report :summary [m]
  (t/with-test-out
    (println "\nRan" (:test m) "tests containing"
             (+ (:pass m) (:fail m) (:error m)) "assertions.")
    (println (:fail m) "failures," (:error m) "errors.")
    (let [stats {:passed (:pass m) :failed (:fail m) :error (:error m)}]
      (rd/summary stats)
      )
    )
  )

(defmacro with-smooth-output
  "Execute body with modified test reporting functions that produce
  smooth output"
  [& body]
  `(binding [t/report smooth-report]
     ~@body))