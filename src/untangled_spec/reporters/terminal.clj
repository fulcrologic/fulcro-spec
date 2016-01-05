(ns untangled-spec.reporters.terminal
  (:require [clojure.test :as t]
            [clojure.stacktrace :as stack]
            [untangled-spec.reporters.impl.terminal :as impl]
            [colorize.core :as c]
            [clojure.data :refer [diff]]
            [io.aviso.exception :refer [format-exception *traditional*]]
            clojure.java.shell)
  (:import clojure.lang.ExceptionInfo
           apple.applescript.AppleScriptEngineFactory))

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

(defn print-test-result [{:keys [message where status actual expected]} print-level]
  (let [print-fn (partial println (space-level print-level))]
    (print-fn)
    (print-fn (if (= status :error)
                "Error" "Failed") "in" where)
    (when message (print-fn "ASSERTION:" message))
    (print-fn "expected:" expected)
    (print-fn "  actual:" actual)
    (print-fn)
    (when true
      ;TODO: ^true -> :key in config?
      (throw (ex-info "" {::stop? true})))))

(defn print-test-item [test-item print-level]
  (t/with-test-out
    (println (space-level print-level)
             (color-str (:status test-item)
                        (:name test-item)))
    (->> (:test-results test-item)
         (remove #(= (:status %) :passed))
         (mapv #(print-test-result % (inc print-level))))
    (->> (:test-items test-item)
         (mapv #(print-test-item % (inc print-level))))))

(defn print-namespace [make-tests-by-namespace]
  (t/with-test-out
    (println)
    (println (color-str (:status make-tests-by-namespace)
                        "Testing " (:name make-tests-by-namespace)))
    (->> (:test-items make-tests-by-namespace)
         (mapv #(print-test-item % 1)))))

(defn notify
  "for more info: https://developer.apple.com/library/mac/documentation/Darwin/Reference/ManPages/man1/osascript.1.html
  & http://apple.stackexchange.com/questions/57412/how-can-i-trigger-a-notification-center-notification-from-an-applescript-or-shel?answertab=votes#tab-top"
  [text & {:keys [title]}]
  (when (= "Mac OS X" (System/getProperty "os.name"))
    (clojure.java.shell/sh "osascript" "-e"
                           (str "display notification \"" text "\" "
                                "with title \"" title "\""))))

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
               error "errors.")
      (when (or (pos? failed) (pos? error))
        (notify (str (+ failed error) " tests failed out of " tested)
                :title (str "clj tests failed"))))))

(defmulti ^:dynamic untangled-report :type)

(defmethod untangled-report :default [m])

(defmethod untangled-report :pass [m]
  (t/inc-report-counter :pass)
  (impl/pass))

(defmethod untangled-report :error [m]
  (t/inc-report-counter :error)
  (impl/error (-> m
                  (merge {:where (clojure.test/testing-vars-str m)})
                  (update :expected str)
                  (update :actual   str))))

(defmethod untangled-report :fail [m]
  (t/inc-report-counter :fail)
  (impl/fail (-> m
                 (merge {:where (clojure.test/testing-vars-str m)})
                 (update :expected str)
                 (update :actual   str))))

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
