(ns smooth-test.behavior)

(defn convert-assertions [forms]
  (if (not= 0 (mod (count forms) 3))
    (throw (ex-info (str "Syntax error in assertions" forms) {})))
  (let [assertions (partition-all 3 forms)]
    (vec (map #(assoc {} :expression `(fn [] ~(first %))
                         ;:arrow (second %)
                         :expected `(fn [] ~(last %))) assertions))
    )
  )

(defn parse-clock-tick [acc [assertion arrow time_ms]]
  (if (= assertion 'clock-ticks)
    (update-in acc [:steps] #(conj % `{:type :clock-tick
                                       :time ~time_ms
                                       :action (fn [] (cljs.pprint/pprint "ADVANCE CLOCK"))
                                       }))
    acc
    )
  )

(defn parse-assertion [acc [assertion arrow result]]
  (if (= assertion 'clock-ticks)
    acc
    (update-in acc [:steps] #(conj % `{:type :assertion
                                       :action (fn [] ~assertion)
                                       :expected (fn [] ~result)
                                       }))
    )
  )

; Behavior: title step+
; step: clock-tick | assertion
; clock-tick: 'clock-ticks '=> ms_number
; assertion: runnable arrow checker
; checker: value | fn
(defmacro behavior [title & forms]
  (let [steps (partition 3 forms)
        result (reduce (fn [acc step] (-> acc (parse-clock-tick step) (parse-assertion step))) { :title title :steps [] } steps)
        ]
  result
  ))

(defmacro binding-wrapper [sym value]
  `(fn [test#] (with-bindings [~sym (smooth-test.behavior/mock ~value)] (test#))))



(defn parse-mocking-clause [acc [the-call arrow value-to-return]]
  (let [function-to-mock (first the-call)
        args-to-expect (rest the-call)]
    (update acc :setup #(conj % {:function function-to-mock :return value-to-return}))))



(defn stackmocks [mocks testname]
  (if (empty? mocks)
    `(~testname)
    `(with-redefs ~(first mocks) ~(stackmocks (rest mocks) testname))
    )
  )



(defn is-mocking-clause? [triple] (not (and (sequential? (first triple)) (= "behavior" (name (first (first triple)))))))

; clauses-and-behaviors: mocking-clause+ behavior+
(defmacro provided [description & clauses-and-behaviors]
  (let [mocking-clauses (take-while is-mocking-clause? (partition-all 3 clauses-and-behaviors))
        behaviors (drop (* 3 (count mocking-clauses)) clauses-and-behaviors)
        setup (reduce parse-mocking-clause { :title description :setup []} mocking-clauses)
        bindings (map (fn [clause] [(:function clause) `(smooth-test.behavior/mock (:return ~clause))]) (:setup setup))
        testname (gensym "test")
        mocks (stackmocks bindings testname )
        mockfn `(fn [~testname] ~mocks)
        ]
    ;(str mockfn)
    `(merge ~@behaviors {:setup ~mockfn})
    )
  )
