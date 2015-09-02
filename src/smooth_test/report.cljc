(ns smooth-test.report
  #?(:clj
     (:require [clojure.test :as t :refer (are is deftest with-test run-tests testing testing-vars-str)]
               [clojure.stacktrace :as stack]
               ))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
             ))
  #?(:cljs (:require [cljs.test :as t]
             )
     )
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  )

(def ^:dynamic *test-level* (atom 0))

(defn space-level []
  (apply str (repeat (* 3 @*test-level*) " ") )
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
                 (stack/print-cause-trace (:actual data) t/*stack-trace-depth*)
                 (prn (:actual data)))))))))


(defmethod smooth-report :default [m]
  )


(defmethod smooth-report :pass [m]
  (t/with-test-out
    (t/inc-report-counter :pass)
    ))

(defmethod smooth-report :error [m]
  (t/with-test-out
    (t/inc-report-counter :error)
    (print-smooth-fail (t/testing-vars-str m))
    (print-diagnostics m)
    ))

(defmethod smooth-report :fail [m]
  (t/with-test-out
    (t/inc-report-counter :fail)
    (println "\nFAIL in" (t/testing-vars-str m))
    (when-let [message (:message m)] (println message))
    (println "expected:" (pr-str (:expected m)))
    (println "  actual:" (pr-str (:actual m)))
    (println)
    ))

(defmethod smooth-report :begin-test-ns [m]
  (t/with-test-out
    (println "\nTesting" (ns-name (:ns m)))
    ))

(defmethod smooth-report :begin-specification [m]
  (t/with-test-out
    (reset! *test-level* 0)
    (println (space-level) (:string m)))
  )

(defmethod smooth-report :end-specification [m]
  (t/with-test-out
    (println)
    (reset! *test-level* 0)
    )
  )

(defmethod smooth-report :begin-behavior [m]
  (t/with-test-out
    (swap! *test-level* inc)
    (println (space-level) (:string m)))
  )

(defmethod smooth-report :end-behavior [m]
  (t/with-test-out
    (swap! *test-level* dec)
    )
  )

(defmethod smooth-report :summary [m]
  (println "\nRan" (:test m) "tests containing"
           (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  )

(defmacro with-smooth-output
  "Execute body with modified test reporting functions that produce
  smooth output"
  [& body]
  `(binding [t/report smooth-report]
     ~@body))