(ns untangled-spec.core
  (:require [clojure.test :as t :refer [do-report test-var *load-tests* *testing-contexts* deftest testing]]
            [clojure.string :as s]
            [untangled-spec.provided :as p]
            [untangled-spec.async :as async]
            [untangled-spec.assertions :refer [triple->assertion]]
            )
  )

(defmacro specification
  "Defines a specificaiton which is translated into a what a deftest macro produces with report hooks for the
   description. Technically outputs a deftest with additional output reporting.
   When *load-tests* is false, the specificaiton is ignored."
  [description & body]
  (let [var-name-from-string (fn [s] (symbol (s/lower-case (s/replace s #"[()~'\"`!@#$;%^& ]" "-"))))
        name (var-name-from-string description)]
    `(~'deftest ~name
       (~'do-report {:type :begin-specification :string ~description})
       ~@body
       (~'do-report {:type :end-specification :string ~description}))
    )
  )

(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
   but must occur inside a specification. If the behavior is not machine
   testable then include the keyword :manual-test just after the string
   description instead of code.

   (behavior \"blows up when the moon is full\" :manual-test)

   "
  [string & body]
  (let [options (into #{} (take-while keyword? body))
        manual-intervention-required (boolean (options :manual-test))
        startkw (if manual-intervention-required :begin-manual :begin-behavior)
        stopkw (if manual-intervention-required :end-manual :end-behavior)
        body (drop-while keyword? body)]
    `(~'testing ~string
       (~'do-report {:type ~startkw :string ~string})
       ~@body
       (~'do-report {:type ~stopkw :string ~string})
       ))
  )

(defmacro component
  "An alias for behavior. Makes some specification code easier to read where a given specification is describing subcomponents of a whole."
  [string & body]
  `(behavior ~string ~@body))

(defmacro with-timeline
  "Adds the infrastructure required for doing timeline testing"
  [& forms]
  `(let [~'*async-queue* (async/make-async-queue)]
     ~@forms
     )
  )

(defmacro async
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
  [string & forms]
  (apply p/provided-fn string forms))

(defmacro when-mocking
  "A macro that works just like 'provided', but requires no string and outputs no extra text in the test output."
  [& forms]
  (apply p/provided-fn :skip-output forms))

(defmacro assertions [& forms]
  (let [triples (partition 3 forms)
        asserts (map triple->assertion triples)]
    `(do ~@asserts)))
