(ns untangled-spec.reporters.suite
  (:require
    [cljs.test :as t]
    [untangled-spec.assertions :as ae]
    [untangled-spec.reporters.impl.base-reporter :as base]))

(defmethod t/assert-expr 'exec [_ msg form]
  `(t/do-report ~(ae/assert-expr 'exec msg form)))

(defmethod t/assert-expr 'throws? [_ msg form]
  `(t/do-report ~(ae/assert-expr 'throws? msg form)))

(defmethod t/assert-expr '= [_ msg form]
  `(t/do-report ~(ae/assert-expr 'eq msg form)))

(defn define-test-methods [state-name test-report-keyword]
  `(let [this# ~state-name]
     (defmethod t/report ~(keyword state-name) [t#])
     (defmethod t/report [~test-report-keyword :pass] [t#]
       (t/inc-report-counter! :pass)
       (base/pass this# t#))
     (defmethod t/report [~test-report-keyword :error] [t#]
       (t/inc-report-counter! :error)
       (base/fail this# t#))
     (defmethod t/report [~test-report-keyword :fail] [t#]
       (t/inc-report-counter! :fail)
       (base/fail this# t#))
     (defmethod t/report [~test-report-keyword :begin-test-ns] [t#]
       (base/begin-namespace this# t#))
     (defmethod t/report [~test-report-keyword :end-test-ns] [t#]
       (base/end-namespace this# t#))
     (defmethod t/report [~test-report-keyword :begin-specification] [t#]
       (base/begin-specification this# t#))
     (defmethod t/report [~test-report-keyword :end-specification] [t#]
       (base/end-specification this# t#))
     (defmethod t/report [~test-report-keyword :begin-manual] [t#]
       (base/begin-manual this# t#))
     (defmethod t/report [~test-report-keyword :end-manual] [t#]
       (base/end-manual this# t#))
     (defmethod t/report [~test-report-keyword :begin-behavior] [t#]
       (base/begin-behavior this# t#))
     (defmethod t/report [~test-report-keyword :end-behavior] [t#]
       (base/end-behavior this# t#))
     (defmethod t/report [~test-report-keyword :begin-provided] [t#]
       (base/begin-provided this# t#))
     (defmethod t/report [~test-report-keyword :end-provided] [t#]
       (base/end-provided this# t#))
     (defmethod t/report [~test-report-keyword :summary] [t#]
       (base/summary this# t#))))

(defn test-suite* [suite-name emit-test-runner]
  (let [state-name (symbol (str suite-name "-state"))
        test-report-keyword (keyword (str *ns* "/" suite-name))
        target (str suite-name)]
    `(do
       (defonce ~state-name (untangled-spec.reporters.suite/new-test-suite ~target))
       (defn ~suite-name []
         ~(emit-test-runner `(t/empty-env ~test-report-keyword))
         (untangled-spec.reporters.suite/render-tests ~state-name))
       (untangled-spec.reporters.suite/render-tests ~state-name)
       ~(define-test-methods state-name test-report-keyword))))

(defmacro deftest-suite [suite-name & test-namespaces]
  (test-suite* suite-name (fn [env] `(t/run-tests ~env ~@test-namespaces))))

(defmacro deftest-all-suite [suite-name regex]
  (test-suite* suite-name (fn [env] `(t/run-all-tests ~regex ~env))))
