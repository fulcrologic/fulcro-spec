(ns smooth-test.parser.parse)

(defn build-advance-clock [tm]
  (let [sym (gensym "advance-clock")]
    `(fn ~sym [level#]
       (smooth-test.core/print-title level# (str "Advancing clock " ~tm "ms")))
    ))

(defn parse-clock-tick [acc [assertion arrow time_ms]]
  (if (= assertion 'clock-ticks)
    (update-in acc [:steps] #(conj % `{:type   :clock-tick
                                       :time   ~time_ms
                                       :action (fn [level#] (smooth-test.core/print-title level# "ADVANCE CLOCK"))
                                       }))
    acc
    )
  )

(defn parse-assertion [acc [assertion arrow result]]
  (if (= assertion 'clock-ticks)
    acc
    (update-in acc [:steps] #(conj % `{:type     :assertion
                                       :action   (fn [] ~assertion)
                                       :expected (fn [] ~result)
                                       }))
    )
  )

(defn stackmocks [mocks testname levelname]
  (if (empty? mocks)
    `(~testname ~levelname)
    `(with-redefs ~(first mocks) ~(stackmocks (rest mocks) testname levelname))
    )
  )

(defn parse-mocking-clause [acc [the-call arrow value-to-return]]
  (let [function-to-mock (first the-call)
        args-to-expect (rest the-call)]
    (update acc :setup #(conj % {:function function-to-mock :return value-to-return}))))


(defn is-mocking-clause? [triple] (not (and (sequential? (first triple)) (= "behavior" (name (first (first triple)))))))

(defn build-assertion [assertion]
  (let [sym (gensym "assertion")]
    `(fn ~sym [level#]
       (let [actual# ((:action ~assertion))
             expected# ((:expected ~assertion))]
         (if (= actual# expected#)
           (smooth-test.core/print-title level# "TEST OK.")
           (smooth-test.core/print-title level# (str "TEST FAILED. Expected " expected# " but got " actual#))
           )
         )
       )
    )
  )
(defn build-behavior-step-function [step]
  (cond
    (= :assertion (:type step)) (build-assertion step)
    (= :clock-tick (:type step)) (build-advance-clock (:time step))
    )
  )


(defn specification-fn [args]
  (let [keywords (set (filter #(keyword? %) args))
        title (first (filter #(string? %) args))
        tests (filter #(list? %) args)
        expanded-tests #spy/d (map #(macroexpand-1 %) tests)
        sym (gensym "specification")]
    `(defn ~(vary-meta sym assoc :test-context keywords) []
       (smooth-test.core/print-title 0 ~title)
       (smooth-test.core/run-sub-tests 0 ~expanded-tests)
       )
    )
  )

(defmacro specification
  "(specification optional-keyword(s) \"title\" test-forms)"
  [& args]
  (specification-fn args))

(defmacro info [description]
  (let [sym (gensym "info")]
    `(fn ~sym [level#] (smooth-test.core/print-title level# ~description))
    )
  )



; Behavior: title step+
; step: clock-tick | assertion
; clock-tick: 'clock-ticks '=> ms_number
; assertion: runnable arrow checker
; checker: value | fn
(defmacro behavior [description & forms]
  (let [steps (partition 3 forms)
        parsed-steps (reduce (fn [acc step] (-> acc (parse-clock-tick step) (parse-assertion step))) {:steps []} steps)
        step-fns (into [] (map build-behavior-step-function (:steps parsed-steps)))
        sym (gensym "behavior")
        ]
    `(fn ~sym [level#]
       (smooth-test.core/print-title level# ~description)
       (smooth-test.core/run-behaviors (inc level#) ~step-fns)
       )
    ))

; clauses-and-behaviors: mocking-clause+ behavior+
(defmacro provided [description & forms]
  (let [mocking-clauses (take-while is-mocking-clause? (partition-all 3 forms))
        non-mocking-clauses (drop (* 3 (count mocking-clauses)) forms)
        behaviors (filter #(= "behavior" (name (first %))) non-mocking-clauses)
        sub-provides (filter #(= "provided" (name (first %))) non-mocking-clauses)
        tests (into [] (macroexpand-1 behaviors))
        testp (into [] (macroexpand-1 sub-provides))
        setup (reduce parse-mocking-clause {:title description :setup []} mocking-clauses)
        bindings (map (fn [clause] [(:function clause) `(smooth-test.core/mock (:return ~clause))]) (:setup setup))
        testname (gensym "test")
        setupname (gensym "setup")
        levelname (gensym "level")
        mocks (stackmocks bindings testname levelname)
        setupfn `(fn ~setupname [~testname ~levelname] ~mocks)
        sym (gensym "provided")
        ]
    `(fn ~sym [level#]
       (let [setupfn# ~setupfn
             behavior-tests# ~tests]
         (smooth-test.core/print-title level# ~description)
         (smooth-test.core/run-behaviors-with-provided (inc level#) setupfn# behavior-tests#)
         )
       )
    )

  )