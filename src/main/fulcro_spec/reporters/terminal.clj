(ns fulcro-spec.reporters.terminal
  (:require
    [clojure.stacktrace :refer [print-stack-trace]]
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as s]
    [clojure.test :as t]
    [colorize.core :as c]
    [io.aviso.exception :as pretty]
    [fulcro-spec.diff :as diff]
    [fulcro-spec.reporter :as base]
    [clojure.string :as str]
    [fulcro-spec.assertions :as ae])
  (:import (clojure.lang ExceptionInfo)))

;; ensure test runners don't see fulcro-spec's extended events
(defmethod t/report :begin-specification [_])
(defmethod t/report :end-specification [_])
(defmethod t/report :begin-behavior [_])
(defmethod t/report :end-behavior [_])
(defmethod t/report :begin-manual [_])
(defmethod t/report :end-manual [_])
(defmethod t/report :begin-provided [_])
(defmethod t/report :end-provided [_])

(def cfg
  "Terminal reporter configuration. You may swap against this atom to set configuration parameters to modify how
   reports are generated.

   Options (defaults are shown) are:

   ```
   {:fail-only?     false ; do not show passing tests
    :color?         true  ; highlight things using terminal colors
    :diff-hl?       false ; print the (unexpected) actual
    :diff?          true  ; Show a diff?

    :diff-list?     false ; print a list of all of the data diffs
    :num-diffs      1     ; max number of diffs if using diff-list?

    :full-diff?     true  ; always show full expected and actual?
    :frame-limit    100   ; Max stack frames on an exception
    :quick-fail?    true  ; stop on the first failure

    ;; Affects output of original assertions
    :*print-level*  3   ; See clojure *print-level*.
    :*print-length* 3}  ; See clojure *print-length*
   ```

   Typically you'd do something like this to change an option:

   ```
   (swap! fulcro-spec.reporters.terminal/cfg assoc :color? false)
   ```

   When using kaocha, see `fulcro-spec.reporters.terminal/*config*`.
   "
  (atom
    {:fail-only?     false
     :quick-fail?    true

     :color?         true
     :diff-hl?       false
     :diff?          true
     :diff-list?     false
     :num-diffs      1
     :full-diff?     true

     :frame-limit    100
     :*print-level*  3
     :*print-length* nil}))

(def ^{:dynamic true
       :doc "Intended for use when running tests through kaocha, as you can set bindings in `tests.edn`.
             Example: `#kaocha/v1 {:bindings {fulcro-spec.reporters.terminal/*config* {:fail-only? true}}}`"}
  *config* {})

(defn env [k] (get *config* k (get @cfg k)))

(defn merge-cfg!
  "For use in the test-refresh repl to change configuration on the fly.
  Single arity will show you the possible keys you can use.
  Passing an empty map will show you the current values."
  ([] (println "Valid cfg keys: " (set (keys @cfg))))
  ([new-cfg]
   (doseq [[k v] new-cfg]
     (assert (contains? @cfg k)
       (str "Invalid key '" k "', try one of these " (set (keys @cfg)))))
   (swap! cfg merge new-cfg)))

(defn color-str [status & strings]
  (let [color?        (env :color?)
        status->color {:normal (comp c/bold c/blue)
                       :diff   (comp c/bold c/cyan)
                       :where  (comp c/bold c/yellow)}
        color-fn      (or (and color? (status->color status))
                        (case status
                          :diff/impl (fn [[got exp]]
                                       ((comp c/bold c/inverse)
                                        (str exp " != " got)))
                          nil)
                        (condp (fn [p x] (pos? (p x 0))) status
                          :fail c/red
                          :error c/red
                          :pass c/green
                          c/reset))]
    (apply color-fn strings)))

(defn pad [pad n] (apply str (repeat n pad)))

(defn space-level [level]
  (pad " " (* 2 level)))

(defn print-throwable [e]
  (print (pretty/format-exception e {:frame-limit (env :frame-limit)}))
  (some-> (.getCause e) print-throwable)
  (when-let [message (and (instance? Exception e) (.getMessage e))]
    (print message)))

(defn pretty-str [s n]
  (as-> (with-out-str (pprint s)) s
    (clojure.string/split s #"\n")
    (apply str (interpose (str "\n" (pad " " (inc (* 2 n)))) s))))

(defn print-highlighted-diff [diff actual]
  (let [process-diff-elem (fn [d]
                            (let [{:keys [got exp]} (diff/extract d)]
                              (color-str :diff/impl [got exp])))
        patched-actual    (diff/patch actual diff process-diff-elem)]
    (println (str \" (color-str :diff/impl ["EXP" "ACT"]) \" \:)
      (pretty-str patched-actual 2))))

(defn print-diff [diff actual print-fn]
  (when (and (seq diff) (env :diff?) (diff/diff? diff))
    (println)
    (when (env :diff-list?)
      (let [num-diffs (env :num-diffs)
            num-diffs (if (number? num-diffs)
                        num-diffs (count diff))]
        (println (color-str :diff "diffs:"))
        (doseq [d (take num-diffs diff)]
          (let [{:keys [exp got path]} (diff/extract d)]
            (when (seq path)
              (println (str "-  at: " path)))
            (println "  exp:" (pretty-str exp 6))
            (println "  got:" (pretty-str got 3))
            (println)))
        (when (< num-diffs (count diff))
          (println "&" (- (count diff) num-diffs) "more..."))))
    (when (and (env :diff-hl?) (coll? actual))
      (print-highlighted-diff diff actual))))

(defn print-message [{:keys [expected message]} print-fn]
  (print-fn (color-str :normal "ASSERTION:"))
  (print-fn)
  (if message
    (print-fn (color-str :normal message))
    (print-fn (color-str :normal expected)))
  (print-fn))

(defn print-extra [e print-fn]
  (print-fn (color-str :normal "    extra:") e))

(defn print-where [w s print-fn]
  (let [status->str {:error "Error"
                     :fail  "Failed"}]
    (->> (s/replace w #"G__\d+" "")
      (str (status->str s) " in ")
      (color-str :where)
      print-fn)))

(def ^:dynamic *stack-frames* 1000)
(def ^:dynamic *exclude-files* #{"core.clj" "stub.cljc"})

(defn- excluded-trace-line? [line]
  (boolean (some #(str/includes? line (str "(" % ":")) *exclude-files*)))

(defn no-core-trace [ex]
  (let [lines       (str/split
                      (with-out-str
                        (print-stack-trace ex))
                      #"\n")
        interesting (take *stack-frames* (remove #(excluded-trace-line? %) lines))]
    (str/join "\n" interesting)))

(defn pr-str-actual [test-result print-level]
  (str "  Actual: "
    (let [actual (or
                   (::ae/actual test-result)
                   (:actual test-result))]
      (cond
        (and (instance? ExceptionInfo actual) (:mock? (ex-data actual)))
        (str "Mocking failure: " (ex-message actual))

        (instance? Exception actual)
        (no-core-trace actual)

        :else
        (pretty-str actual (+ 5 print-level))))))

(defn pr-str-expected [test-result print-level]
  (str "Expected: "
    (let [expected (or
                     (::ae/expected test-result)
                     (:expected test-result))]
      (pretty-str expected (+ 5 print-level)))))

(defn format-compiler-error [error]
  (pretty/format-exception error {:frame-limit 0}))

(defn print-test-result [{:keys     [where status extra throwable diff]
                          ::ae/keys [actual] :as test-result}
                         print-fn print-level]
  (print-fn)
  (-> (or where "Unknown") (print-where status print-fn))
  (if-let [load-error (:kaocha.testable/load-error (:kaocha/testable test-result))]
    (print-fn (format-compiler-error load-error))
    (do
      (when (and (= status :error) (instance? Throwable actual))
        (print-throwable actual))
      (when (and throwable (not (instance? Throwable actual)))
        (print-throwable throwable))
      (print-message test-result print-fn)
      (when (env :full-diff?)
        (print-fn (pr-str-actual test-result print-level))
        (print-fn (pr-str-expected test-result print-level)))
      (some-> extra (print-extra print-fn))
      (some-> diff (print-diff actual print-fn))
      (when (env :quick-fail?)
        (throw (ex-info "" {::stop? true}))))))

(def when-fail-only-keep-failed
  (partial filter
    #(or
       (not (env :fail-only?))
       (pos? (:fail (:status %) 0))
       (pos? (:error (:status %) 0)))))

(defn print-test-item [test-item print-level]
  (let [status  (:status test-item)
        failed? (and (map? status) (or (pos-int? (:fail status)) (pos-int? (:error status))))]
    (if (= "unmarked" (:name test-item))
      (when failed?
        (println (space-level print-level)
          (color-str (:status test-item)
            "UNMARKED ASSERTION/TEST")))
      (println (space-level print-level)
        (color-str (:status test-item)
          (:name test-item)))))
  (let [to-report (filter (comp #{:fail :error} :status)
                    (:test-results test-item))
        p #(print-test-result % (->> print-level inc space-level
                                  (partial println))
             (inc print-level))]
    (->> to-report
      (remove :mock?)
      (mapv p))
    (->> (:test-items test-item)
      (when-fail-only-keep-failed)
      (mapv #(print-test-item % (inc print-level))))
    (->> to-report
      (filter :mock?)
      (mapv p))))

(defn print-namespace [make-tests-by-namespace]
  (println)
  (println (color-str (:status make-tests-by-namespace)
             "Testing " (:name make-tests-by-namespace)))
  (->> (:test-items make-tests-by-namespace)
    (when-fail-only-keep-failed)
    (mapv #(print-test-item % 1))))

(defn print-test-report [{:as test-report
                          :keys [test-results namespaces test pass fail error]}]
  (println "Running tests for:" (map :name namespaces))
  (try
    (->> test-results
      (mapv #(print-test-result % println 1)))
    (->> namespaces
      (when-fail-only-keep-failed)
      (sort-by :name)
      (mapv print-namespace))
    (catch Exception e
      (when-not (->> e ex-data ::stop?)
        (print-throwable e))))
  (println "\nRan" test "tests containing"
    (+ pass fail error) "assertions.")
  (println fail "failures," error "errors."))

(defn print-reporter
  "Prints the current report data from the report data state and applies colors based on test results"
  [reporter]
  (defmethod print-method Throwable [e w]
    (print-method (c/red e) w))
  (t/with-test-out
    (let [test-report (base/get-test-report reporter)]
      (if-let [kaocha-error (some-> test-report :kaocha/test-plan :kaocha.watch/error)]
        (println "\n" (format-compiler-error kaocha-error))
        (print-test-report test-report))))
  (remove-method print-method Throwable)
  reporter)

(def this (base/make-test-reporter))

(def fulcro-report
  (base/fulcro-report {:test/reporter this}
    (comp base/reset-test-report! print-reporter :test/reporter)))
