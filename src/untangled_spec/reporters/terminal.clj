(ns untangled-spec.reporters.terminal
  (:require
    [clojure.edn :as edn]
    [clojure.pprint :refer [pprint]]
    [clojure.stacktrace :as stack]
    [clojure.string :as s]
    [clojure.test :as t]
    [clojure.walk :as walk]
    [colorize.core :as c]
    [io.aviso.exception :as pretty]
    [untangled-spec.reporters.impl.diff :as diff]
    [untangled-spec.reporters.impl.terminal :as impl]
    [untangled-spec.reporters.impl.base-reporter :as base]))

(def cfg
  (atom
    (let [COLOR        (System/getenv "US_DIFF_HL")
          DIFF_MODE    (System/getenv "US_DIFF_MODE")
          DIFF         (System/getenv "US_DIFF")
          NUM_DIFFS    (System/getenv "US_NUM_DIFFS")
          FRAME_LIMIT  (System/getenv "US_FRAME_LIMIT")
          QUICK_FAIL   (System/getenv "US_QUICK_FAIL")
          FAIL_ONLY    (System/getenv "US_FAIL_ONLY")
          PRINT_LEVEL  (System/getenv "US_PRINT_LEVEL")
          PRINT_LENGTH (System/getenv "US_PRINT_LENGTH")]
      {:fail-only?      (#{"1" "true"}  FAIL_ONLY)
       :color?          (#{"1" "true"}  COLOR)
       :diff-hl?        (#{"hl" "all"}  DIFF_MODE)
       :diff-list? (not (#{"hl"}        DIFF_MODE))
       :diff?      (not (#{"0" "false"} DIFF))
       :frame-limit (edn/read-string (or FRAME_LIMIT "10"))
       :num-diffs  (edn/read-string (or NUM_DIFFS "1"))
       :quick-fail? (not (#{"0" "false"} QUICK_FAIL))
       :*print-level* (edn/read-string (or PRINT_LEVEL "3"))
       :*print-length* (edn/read-string (or PRINT_LENGTH "2"))})))
(defn env [k] (get @cfg k))
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
  (let [color? (env :color?)
        status->color {:normal (comp c/bold c/yellow)
                       :diff (comp c/bold c/cyan)
                       :where (comp c/bold c/white)}
        color-fn (or (and color? (status->color status))
                     (case status
                       :diff/impl (fn [[got exp]]
                                    ((comp c/bold c/inverse)
                                     (str exp " != " got)))
                       nil)
                     (condp (fn [p x] (pos? (p x 0))) status
                       :passed c/green
                       :failed c/red
                       :error  c/red
                       c/reset))]
    (apply color-fn strings)))

(defn pad [pad n] (apply str (repeat n pad)))

(defn space-level [level]
  (pad " " (* 2 level)))

(defn print-throwable [e]
  (print (pretty/format-exception e {:frame-limit (env :frame-limit)}))
  (some-> (.getCause e) print-throwable))

(defmethod print-method Throwable [e w]
  (print-method (c/red e) w))

(defn pretty-str [s n]
  (as-> (with-out-str (pprint s)) s
    (clojure.string/split s #"\n")
    (apply str (interpose (str "\n" (pad " " (inc (* 2 n)))) s))))

(defn print-highligted-diff [diff actual]
  (let [process-diff-elem (fn [d]
                            (let [{:keys [got exp]} (diff/extract d)]
                              (color-str :diff/impl [got exp])))
        patched-actual (diff/patch actual diff process-diff-elem)]
    (println (str \" (color-str :diff/impl ["EXP" "ACT"]) \"\:)
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
      (print-highligted-diff diff actual))))

(defn ?ellipses [s]
  (binding [*print-level* (env :*print-level*)
            *print-length* (env :*print-length*)]
    (try (apply str (drop-last (with-out-str (pprint (edn/read-string s)))))
      (catch Error _ s))))

(defn parse-message [m]
  (try (->> (edn/read-string (str "[" m "]"))
         (sequence (comp (map str) (map base/fix-str)))
         (zipmap [:actual :arrow :expected]))
    (catch Error _ {:message m})))

(defn print-message [m print-fn]
  (print-fn (color-str :normal "ASSERTION:")
    (let [{:keys [arrow actual expected message]} (parse-message m)]
      (or message
          (str (-> actual ?ellipses)
            " " arrow
            " " (-> expected ?ellipses))))))

(defn print-extra [e print-fn]
  (print-fn (color-str :normal "    extra:") e))

(defn print-where [w s print-fn]
  (let [status->str {:error "Error"
                     :failed "Failed"}]
    (->> (s/replace w #"G__\d+" "")
         (str (status->str s) " in ")
         (color-str :where)
         print-fn)))

(defn print-test-result [{:keys [message where status actual
                                 expected extra throwable diff]}
                         print-fn print-level]
  (print-fn)
  (some-> where (print-where status print-fn))
  (when (and (= status :error)
             (instance? Throwable actual))
    (print-throwable actual))
  (when (and throwable
             (not (instance? Throwable actual)))
    (print-throwable throwable))
  (some-> message (print-message print-fn))
  (when (or (not diff) (empty? diff)
            (not (env :diff?))
            (and (not (env :diff-hl?))
                 (not (env :diff-list?))))
    (print-fn "   Actual:" (pretty-str actual (+ 5 print-level)))
    (print-fn " Expected:" (pretty-str expected (+ 5 print-level))))
  (some-> extra (print-extra print-fn))
  (some-> diff (print-diff actual print-fn))
  (when (env :quick-fail?)
    (throw (ex-info "" {::stop? true}))))

(def when-fail-only-keep-failed
  (filter #(if-not (env :fail-only?) true
             (or (pos? (:failed (:status %) 0))
                 (pos? (:error (:status %) 0))))))

(defn print-test-item [test-item print-level]
  (t/with-test-out
    (println (space-level print-level)
      (color-str (:status test-item)
        (:name test-item)))
    (into []
      (comp (filter (comp #{:failed :error} :status))
        (map #(print-test-result % (->> print-level inc space-level
                                     (partial println))
                (inc print-level))))
      (:test-results test-item))
    (into []
      (comp when-fail-only-keep-failed
        (map #(print-test-item % (inc print-level))))
      (:test-items test-item))))

(defn print-namespace [make-tests-by-namespace]
  (t/with-test-out
    (println)
    (println (color-str (:status make-tests-by-namespace)
               "Testing " (:name make-tests-by-namespace)))
    (into []
      (comp when-fail-only-keep-failed
        (map #(print-test-item % 1)))
      (:test-items make-tests-by-namespace))))

(defn print-report-data
  "Prints the current report data from the report data state and applies colors based on test results"
  []
  (t/with-test-out
    (let [{:keys [namespaces tested passed failed error]} @impl/*test-state*]
      (try (->> namespaces
             (into [] when-fail-only-keep-failed)
             (sort-by :name)
             (mapv print-namespace))
        (catch Exception e
          (when-not (->> e ex-data ::stop?)
            (print-throwable e))))
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
  (impl/error (-> m (merge {:where (clojure.test/testing-vars-str m)}))))

(defmethod untangled-report :fail [m]
  (t/inc-report-counter :fail)
  (impl/fail (-> m (merge {:where (clojure.test/testing-vars-str m)}))))

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
