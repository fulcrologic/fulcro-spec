(ns untangled-spec.core
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]
    [clojure.test]
    [untangled-spec.assertions :as ae]
    [untangled-spec.async :as async]
    [untangled-spec.impl.macros :as im]
    [untangled-spec.provided :as p]
    [untangled-spec.stub]
    [untangled-spec.spec :as us]))

(defn- cljs-env?
  "https://github.com/Prismatic/schema/blob/master/src/clj/schema/macros.clj"
  [env] (boolean (:ns env)))

(defn- if-cljs [env cljs clj]
  (if (cljs-env? env) cljs clj))

(defmethod clojure.test/assert-expr '= [msg form]
  `(clojure.test/do-report ~(ae/assert-expr msg form)))

(defmethod clojure.test/assert-expr 'exec [msg form]
  `(clojure.test/do-report ~(ae/assert-expr msg form)))

(defmethod clojure.test/assert-expr 'throws? [msg form]
  `(clojure.test/do-report ~(ae/assert-expr msg form)))

(defn var-name-from-string [s]
  (symbol (str "__" (str/replace s #"[^\w\d\-\!\#\$\%\&\*\_\<\>\:\?\|]" "-") "__")))

(s/def ::specification
  (s/cat
    :name string?
    :opts (s/* keyword?)
    :body (s/* ::us/any)))

(s/fdef specification :args ::specification)
(defmacro specification
  "Defines a specification which is translated into a what a deftest macro produces with report hooks for the
   description. Technically outputs a deftest with additional output reporting.
   When *load-tests* is false, the specification is ignored."
  [& args]
  (let [{:keys [name opts body]} (us/conform! ::specification args)
        var-name (var-name-from-string name)
        prefix (if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "deftest")
       ~(with-meta (symbol (str var-name (gensym)))
          (zipmap opts (repeat true)))
       (im/with-reporting {:type :specification :string ~name}
         ~@body))))

(s/def ::behavior ::specification)
(s/fdef behavior :args ::behavior)
(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
   but must occur inside a specification. If the behavior is not machine
   testable then include the keyword ::manual-test just after the behavior name
   instead of code.

   (behavior \"blows up when the moon is full\" ::manual-test)"
  [& args]
  (let [{:keys [name opts body]} (us/conform! ::behavior args)
        typekw (if (contains? opts :manual-test)
                 :manual :behavior)
        prefix (if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "testing") ~name
       (im/with-reporting ~{:type typekw :string name}
         ~@body))))

(defmacro component
  "An alias for behavior. Makes some specification code easier to read where a given specification is describing subcomponents of a whole."
  [& args] `(behavior ~@args))

(defmacro with-timeline
  "Adds the infrastructure required for doing timeline testing"
  [& forms]
  `(let [~'*async-queue* (async/make-async-queue)]
     ~@forms))

(defmacro async
  "Adds an event to the event queue with the specified time and callback function.
   Must be wrapped by with-timeline.
   "
  [tm cb]
  `(async/schedule-event ~'*async-queue* ~tm (fn [] ~cb)))

(defmacro tick
  "Advances the timer by the specified number of ticks.
   Must be wrapped by with-timeline."
  [tm]
  `(async/advance-clock ~'*async-queue* ~tm))

(defmacro provided
  "A macro for using a Midje-style provided clause within any testing framework. This macro rewrites
   assertion-style mocking statements into code that can do that mocking.
   See the doc string for `p/parse-arrow-count`."
  [string & forms]
  (apply p/provided-fn (cljs-env? &env) string forms))

(defmacro when-mocking
  "A macro that works just like 'provided', but requires no string and outputs no extra text in the test output.
   See the doc string for `p/parse-arrow-count`."
  [& forms]
  (apply p/provided-fn (cljs-env? &env) :skip-output forms))

(s/fdef assertions :args ::ae/assertions)
(defmacro assertions [& forms]
  (let [blocks (us/conform! ::ae/assertions forms)
        asserts (map (partial ae/block->asserts (cljs-env? &env)) blocks)]
    `(do ~@asserts true)))
