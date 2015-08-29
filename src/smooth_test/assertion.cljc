(ns smooth-test.assertion
  #?(:clj
     (:require [clojure.test :refer [function?]]))
  #?(:cljs (:require-macros
             [smooth-test.assertion :as a]
             ))
  #?(:cljs (:require [cljs.test :as t])))

#?(:cljs (defn function? [arg]
           (let [getType (clj->js {})]
             (and arg (.call (.-toString getType) arg))
             )
           ))

(defn is-value?
  "Returns true if the given argument is considered a 'plain value'."
  [arg]
  (or (map? arg)
      (vector? arg)
      (set? arg)
      (string? arg)
      (number? arg)
      )
  )

(defn is-function?
  "Detect if the given argument is a function"
  [arg]
  (function? arg)
  )

#?(:clj
   (defn captured-invocation [desired-invocation]
     (list 'try desired-invocation '(catch e {::failed e}))
     )
   )

#?(:clj
   (defn is-lambda-form? [form]
     (and (list? form) (re-find #"^fn" (name (first form))))
     )
   )

#?(:clj (defn passed [d] d))
#?(:clj (defn failed [d r] d))

#?(:clj
   (defn regular-assertion [invocation checker test-def]
     `(let [result# ~invocation]
        (cond
          (and result# (map? result#) (contains? result# ::failed)) (failed ~test-def result#)
          (~checker result#) (passed ~test-def)
          :else (failed ~test-def result#)
          ))
     )
   )

#?(:clj
   (defn rewrite-arrow
     "Interpret the arrow of a behavior. The invocation must be a form that will result in a real value or the special 
     {::failed e} to indicate an exception was thrown. The checker must be a form that can take a value and return t/f"
     [invocation arrow checker]
     (cond
       (= '=> arrow) (regular-assertion invocation checker 1)
       ;(= '=throws=> arrow) (throwing-assertion invocation checker)
       :else (throw (ex-info "Invalid arrow used in behavior assertion." {:problem-arrow arrow}))
       )
     )
   )

;#?(:clj 
;   (defn build-checker [expectation]
;     (if (list? expectation))
;     '(if (is-value? expectation))
;     )
;   )

#?(:clj
   (defn build-assertion-fn [form]
     (let [invocation (captured-invocation (first form))
           arrow (second form)
           expectation (last form)
           ]

       )
     )
   )

#?(:clj
   (defmacro build-assertion [form]
     (assert (= 3 (count form)) "Assertions must be triples!")
     (build-assertion-fn form)
     )
   )

#?(:clj
   (defmacro assertions [descr & forms]
     (let [triples (partition 3 forms)
           asserts (map (fn [[actual _ expected]] (list 'is (list '= actual expected) (str "ASSERTION: " actual " => " expected))) triples)
           ]
       `(~'testing ~descr
                 ~@asserts
                 ))
     )
   )
