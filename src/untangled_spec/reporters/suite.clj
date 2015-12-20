(ns untangled-spec.reporters.suite)

(defn define-test-methods [name test-report-keyword]
  `((cljs.core/defmethod cljs.test/report ~(keyword name) [~'m])
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :pass] [~'m]
      (cljs.test/inc-report-counter! :pass)
      (untangled-spec.reporters.impl.suite/pass ~name))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :error] [~'m]
      (cljs.test/inc-report-counter! :error)
      (let [~'detail {:where    (cljs.test/testing-vars-str ~'m)
                      :message  (:message ~'m)
                      :expected (:expected ~'m)
                      :actual   (:actual ~'m)}]
        (untangled-spec.reporters.impl.suite/fail ~name ~'detail)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :fail] [~'m]
      (cljs.test/inc-report-counter! :fail)
      (let [~'detail {:where    (cljs.test/testing-vars-str ~'m)
                      :message  (:message ~'m)
                      :expected (:expected ~'m)
                      :actual   (:actual ~'m)}]
        (untangled-spec.reporters.impl.suite/fail ~name ~'detail)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :begin-test-ns] [~'m]
      (untangled-spec.reporters.impl.suite/begin-namespace ~name (cljs.core/name (:ns ~'m))))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :begin-specification] [~'m]
      (untangled-spec.reporters.impl.suite/begin-specification ~name (:string ~'m)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :end-specification] [~'m]
      (untangled-spec.reporters.impl.suite/end-specification ~name))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :begin-manual] [~'m]
      (untangled-spec.reporters.impl.suite/begin-manual ~name (:string ~'m)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :end-manual] [~'m]
      (untangled-spec.reporters.impl.suite/end-manual ~name))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :begin-behavior] [~'m]
      (untangled-spec.reporters.impl.suite/begin-behavior ~name (:string ~'m)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :end-behavior] [~'m]
      (untangled-spec.reporters.impl.suite/end-behavior ~name))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :begin-provided] [~'m]
      (untangled-spec.reporters.impl.suite/begin-provided ~name (:string ~'m)))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :end-provided] [~'m]
      (untangled-spec.reporters.impl.suite/end-provided ~name))
    (cljs.core/defmethod cljs.test/report [~test-report-keyword :summary] [~'m]
      (let [~'stats {:passed (:pass ~'m) :failed (:fail ~'m) :error (:error ~'m)}]
        (untangled-spec.reporters.impl.suite/summary ~name ~'stats)))))

(defmacro test-suite [name & test-namespaces]
  (let [state-name (symbol (str name "-state"))
        test-report-keyword (keyword (str *ns* "/" name))
        target (str name)]
    `(do
       (cljs.core/defonce ~state-name (untangled-spec.reporters.impl.suite/new-test-suite ~target))
       (cljs.core/defn ~name []
         (cljs.test/run-tests (cljs.test/empty-env ~test-report-keyword) ~@test-namespaces)
         (untangled-spec.reporters.impl.suite/render-tests ~state-name))
       (untangled-spec.reporters.impl.suite/render-tests ~state-name)
       ~@(define-test-methods state-name test-report-keyword))))
