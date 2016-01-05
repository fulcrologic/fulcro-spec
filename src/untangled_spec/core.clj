(ns untangled-spec.core
  (:require [clojure.string :as s]
            [untangled-spec.provided :as p]
            [untangled-spec.async :as async]
            [untangled-spec.assertions :refer [triple->assertion]]
            [untangled-spec.stub]
            [untangled-spec.assert-expr :as ae]
            [clojure.test]))

(defn cljs-env?
  "https://github.com/Prismatic/schema/blob/master/src/clj/schema/macros.clj"
  [env] (boolean (:ns env)))

(defn if-cljs [env cljs clj]
  (if (cljs-env? env) cljs clj))

(defmethod clojure.test/assert-expr 'call [msg form]
  `(clojure.test/do-report ~(ae/assert-expr 'call msg form)))

(defmethod clojure.test/assert-expr 'clojure.core/= [msg form]
  `(clojure.test/do-report ~(ae/assert-expr 'eq msg form)))

(defmethod clojure.test/assert-expr 'throws? [msg form]
  `(clojure.test/do-report ~(ae/assert-expr 'throws? msg form)))

(defmacro specification
  "Defines a specificaiton which is translated into a what a deftest macro produces with report hooks for the
  description. Technically outputs a deftest with additional output reporting.
  When *load-tests* is false, the specificaiton is ignored."
  [description & body]
  (let [var-name-from-string (fn [s] (symbol (s/lower-case (s/replace s #"[()~'\"`!@#$;%^& ]" "-"))))
        name (var-name-from-string description)
        prefix (if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "deftest") ~name
               (~(symbol prefix "do-report")
                         {:type :begin-specification :string ~description})
               ~@body
               (~(symbol prefix "do-report")
                         {:type :end-specification :string ~description}))))

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
        body (drop-while keyword? body)
        prefix (if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "testing") ~string
               (~(symbol prefix "do-report")
                         {:type ~startkw :string ~string})
               ~@body
               (~(symbol prefix "do-report")
                         {:type ~stopkw :string ~string})
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
  (apply p/provided-fn (cljs-env? &env) string forms))

(defmacro when-mocking
  "A macro that works just like 'provided', but requires no string and outputs no extra text in the test output."
  [& forms]
  (apply p/provided-fn (cljs-env? &env) :skip-output forms))

(defmacro assertions [& forms]
  (let [triples (partition 3 forms)
        asserts (map (partial triple->assertion (cljs-env? &env)) triples)]
    `(do ~@asserts)))
