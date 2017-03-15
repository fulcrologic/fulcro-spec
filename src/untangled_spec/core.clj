(ns untangled-spec.core
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]
    [clojure.test]
    [untangled-spec.assertions :as ae]
    [untangled-spec.async :as async]
    [untangled-spec.impl.macros :as im]
    [untangled-spec.provided :as p]
    [untangled-spec.runner] ;;side effects
    [untangled-spec.selectors :as sel]
    [untangled-spec.stub]
    [untangled-spec.spec :as us]))

(defn var-name-from-string [s]
  (symbol (str "__" (str/replace s #"[^\w\d\-\!\#\$\%\&\*\_\<\>\:\?\|]" "-") "__")))

(s/def ::specification
  (s/cat
    :name string?
    :selectors (s/* keyword?)
    :body (s/* ::us/any)))

(s/fdef specification :args ::specification)
(defmacro specification
  "Defines a specification which is translated into a what a deftest macro produces with report hooks for the
   description. Technically outputs a deftest with additional output reporting.
   When *load-tests* is false, the specification is ignored."
  [& args]
  (let [{:keys [name selectors body]} (us/conform! ::specification args)
        test-name (-> (var-name-from-string name)
                    (str (gensym)) symbol
                    (with-meta (zipmap selectors (repeat true))))
        prefix (im/if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "deftest") ~test-name
       (im/when-selected-for ~(us/conform! ::sel/test-selectors selectors)
         (im/with-reporting {:type :specification :string ~name}
           (im/try-report ~name
             ~@body))))))

(s/def ::behavior (s/cat
                    :name (constantly true)
                    :opts (s/* keyword?)
                    :body (s/* ::us/any)))
(s/fdef behavior :args ::behavior)

(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
   but must occur inside a specification. If the behavior is not machine
   testable then include the keyword :manual-test just after the behavior name
   instead of code.

   (behavior \"blows up when the moon is full\" :manual-test)"
  [& args]
  (let [{:keys [name opts body]} (us/conform! ::behavior args)
        typekw (if (contains? opts :manual-test)
                 :manual :behavior)
        prefix (im/if-cljs &env "cljs.test" "clojure.test")]
    `(~(symbol prefix "testing") ~name
       (im/with-reporting ~{:type typekw :string name}
         (im/try-report ~name
           ~@body)))))

(defmacro component
  "An alias for behavior. Makes some specification code easier to read where a given specification is describing subcomponents of a whole."
  [& args] `(behavior ~@args))

(defmacro provided
  "A macro for using a Midje-style provided clause within any testing framework.
   This macro rewrites assertion-style mocking statements into code that can do that mocking.
   See the clojure.spec for `::p/mocks`.
   See the doc string for `p/parse-arrow-count`."
  [string & forms]
  (p/provided* (im/cljs-env? &env) string forms))

(defmacro when-mocking
  "A macro that works just like 'provided', but requires no string and outputs no extra text in the test output.
   See the clojure.spec for `::p/mocks`.
   See the doc string for `p/parse-arrow-count`."
  [& forms]
  (p/provided* (im/cljs-env? &env) :skip-output forms))

(s/fdef assertions :args ::ae/assertions)
(defmacro assertions [& forms]
  (let [blocks (ae/fix-conform (us/conform! ::ae/assertions forms))
        asserts (map (partial ae/block->asserts (im/cljs-env? &env)) blocks)]
    `(do ~@asserts true)))

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
