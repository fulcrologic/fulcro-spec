(ns smooth-test.core
  (:require [clojure.test :refer [do-report test-var *load-tests* *testing-contexts*]]
            [clojure.string :as s]
            [smooth-test.provided :as p]
            [smooth-test.async :as async]
            )
  )

(defmacro specification
  "Defines a specificaiton which is translated into a what a deftest macro produces with report hooks for the
   description.
   When *load-tests* is false, specificaiton is ignored."
  [description & body]
  (when *load-tests*
    (let [var-name-from-string (fn [s] (symbol (s/lower-case (s/replace s #"[ ]" "-"))))
          name (var-name-from-string description)]
      `(def ~(vary-meta name assoc :test `(fn []
                                            (do-report {:type :begin-specification :string ~description})
                                            ~@body
                                            (do-report {:type :end-specification :string ~description})
                                            ))
         (fn []
           (test-var (var ~name)))))))

(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
   but must occur inside a test function (deftest)."
  [string & body]
  `(binding [*testing-contexts* (conj *testing-contexts* ~string)]
     (do-report {:type :begin-behavior :string ~string})
     ~@body
     (do-report {:type :end-behavior :string ~string}))
  )

(defmacro with-timeline
  "Adds the infrastructure required for doing timeline testing"
  [& forms]
  `(let [~'*async-queue* (async/make-async-queue)]
     ~@forms
     )
  )


(defmacro event
  "Adds an event to the event queue with the specified time and callback function.
Must be wrapped by with-timeline.
"
  [tm cb]
  `(async/schedule-event ~'*async-queue* ~tm (fn [] ~cb))
  )

(defmacro tick
  "Advances the timer by the specified number of ticks.
   Must be wrapped by with-timeline."
  [tm]
  `(async/advance-clock ~'*async-queue* ~tm)
  )

(defmacro provided
  "A macro for using a Midje-style provided clause within any testing framework. This macro rewrites
  assertion-style mocking statements into code that can do that mocking.
  "
  [& forms]
  (apply p/provided-fn forms))

(defmacro assertions [descr & forms]
  (let [triples (partition 3 forms)
        asserts (map (fn [[actual _ expected]] (list 'is (list '= actual expected) (str "ASSERTION: " actual " => " expected))) triples)
        ]
    `(~'behavior ~descr
       ~@asserts
       ))
  )
