(ns untangled-spec.provided
  (:require [clojure.string :as str]
            [untangled-spec.stub :as stub]
            )
  )

(defn parse-arrow-count [sym]
  (let [nm (name sym)
        number (re-find #"\d+" nm)]
    (assert (re-find #"^=" nm) "Arrows must start with = (try =#x=>)")
    (assert (re-find #"=>$" nm) "Arrows must end with => (try =#x=>)")
    (cond
      (= "=>" nm) :many
      (= "0" number) (assert false "Arrow count must not be zero in provided clauses.")
      number (Integer/parseInt number)
      )
    )
  )

(defn parse-mock-triple [[thing-to-mock arrow thing-to-do]]
  (assert (list? thing-to-mock) "Provided clause must have function calls on the left of triples")
  (assert (every? symbol? thing-to-mock) "Provided clauses must have symbols only. No raw data.")
  (let [arglist (rest thing-to-mock)]
    {;:stub-symbol   (gensym "stub")
     :ntimes         (parse-arrow-count arrow)
     :stub-function  `(fn [~@arglist] ~thing-to-do)
     :symbol-to-mock (first thing-to-mock)
     }
    ))

(defn convert-groups-to-symbolic-triples [grouped-mocks]
  (letfn [(steps-to-script [acc [sym detailed-steps]]
                           (let [steps (vec (map (fn [detail] `(stub/make-step ~(:stub-function detail) ~(:ntimes detail))) detailed-steps))]
                             (conj acc [sym (gensym "script") `(stub/make-script ~(name sym) ~steps)])
                             )
                           )]
    (reduce steps-to-script [] grouped-mocks)
    ))

(defn is-arrow? [sym]
  (and (symbol? sym)
       (re-find #"^=" (name sym))
       )
  )

(defn provided-fn
  [string & forms]
  (let [groups (partition-all 3 forms)
        triples (take-while #(and (= 3 (count %)) (is-arrow? (second %))) groups)
        behaviors (drop (* 3 (count triples)) forms)
        parsed-mocks (reduce (fn [acc t] (conj acc (parse-mock-triple t))) [] triples)
        grouped-mocks (group-by :symbol-to-mock parsed-mocks)
        script-triples (convert-groups-to-symbolic-triples grouped-mocks)
        script-let-pairs (reduce (fn [acc ele] (concat acc [(second ele) (last ele)])) [] script-triples)
        redef-pairs (reduce (fn [acc ele] (concat acc [(first ele) `(stub/scripted-stub ~(second ele))])) [] script-triples)
        script-symbols (reduce (fn [acc ele] (concat acc [(second ele)])) [] script-triples)
        ]
    (if (= :skip-output string)
      `(let [~@script-let-pairs]
         (with-redefs [~@redef-pairs]
           ~@behaviors
           (stub/validate-target-function-counts [~@script-symbols])
           ))
      `(let [~@script-let-pairs]
         (with-redefs [~@redef-pairs]
           (~'do-report {:type :begin-provided :string ~string})
           ~@behaviors
           (stub/validate-target-function-counts [~@script-symbols])
           (~'do-report {:type :end-provided :string ~string})
           ))
      )))
