(ns fulcro-spec.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha]
    [clojure.string :as str]
    [clojure.test]
    [fulcro-spec.assertions :as ae]
    [fulcro-spec.async :as async]
    [fulcro-spec.hooks :refer [hooks]]
    [fulcro-spec.impl.macros :as im]
    [fulcro-spec.provided :as p]
    [fulcro-spec.spec :as fss]
    [fulcro-spec.stub]))

(declare => =1x=> =2x=> =3x=> =4x=> =throws=> =fn=> =check=>)

(defn set-hooks!
  "Call this to set the `:on-enter` and `:on-leave` hooks.
   Currently only `specification`, `behavior`, and `component` call these hooks.
   `:on-enter` and `:on-leave` will be called with a single argument,
   a map with the test or behavior string and its location (currently just line number).
   See the macro source code for exact and up to date details."
  [handlers]
  (reset! hooks handlers))

(defn var-name-from-string [s]
  (symbol (str "__" (str/replace s #"[^\w\d\-\!\#\$\%\&\*\_\<\>\:\?\|]" "-") "__")))

(s/def ::specification
  (s/cat
    :metadata (s/? map?)
    :name string?
    :selectors (s/* keyword?)
    :body (s/* ::fss/any)))

(s/fdef specification :args ::specification)
(defmacro specification
  "Defines a specification which is translated into a what a deftest macro produces with report hooks for the
   description. Technically outputs a deftest with additional output reporting.
   When *load-tests* is false, the specification is ignored.

   Usage:
     (specification \"test name\" ...)
     (specification \"test name\" :focus ...)
     (specification {:integration true} \"test name\" ...)
     (specification {:integration true :slow true} \"test name\" :focus ...)

   An optional metadata map can appear before the test name to add custom metadata to the test var.
   Keyword selectors (like :focus) can follow the test name and are converted to metadata (e.g., {:focus true}).
   Both metadata sources are merged, with selector keywords taking precedence over the metadata map."
  [& args]
  (let [{:keys [metadata name selectors body]} (fss/conform! ::specification args)
        selector-meta (zipmap selectors (repeat true))
        combined-meta (merge metadata selector-meta)
        test-name     (-> (var-name-from-string name)
                        (with-meta combined-meta))
        prefix        (im/if-cljs &env "cljs.test" "clojure.test")
        form-meta     (select-keys (meta &form) [:line])
        hook-info     {::specification name
                       ::location      form-meta}]
    `(~(symbol prefix "deftest") ~test-name
       (im/with-reporting {:type      :specification
                           :string    ~name
                           :form-meta ~form-meta}
         ((:on-enter @hooks (fn [& _#])) ~hook-info)
         (let [result# (im/try-report ~name ~@body)]
           ((:on-leave @hooks (fn [& _#])) ~hook-info)
           result#)))))

(s/def ::behavior (s/cat
                    :name (constantly true)
                    :opts (s/* keyword?)
                    :body (s/* ::fss/any)))
(s/fdef behavior :args ::behavior)

(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
   but must occur inside a specification. If the behavior is not machine
   testable then include the keyword :manual-test just after the behavior name
   instead of code.

   (behavior \"blows up when the moon is full\" :manual-test)"
  [& args]
  (let [{:keys [name opts body]} (fss/conform! ::behavior args)
        typekw    (if (contains? opts :manual-test)
                    :manual :behavior)
        prefix    (im/if-cljs &env "cljs.test" "clojure.test")
        form-meta (select-keys (meta &form) [:line])
        hook-info {::behavior name
                   ::location form-meta}]
    `(~(symbol prefix "testing") ~name
       (im/with-reporting ~{:type typekw :string name}
         ((:on-enter @hooks (fn [& _#])) ~hook-info)
         (let [result# (im/try-report ~name ~@body)]
           ((:on-leave @hooks (fn [& _#])) ~hook-info)
           result#)))))

(defmacro component
  "An alias for behavior. Makes some specification code easier to read where a given specification is describing subcomponents of a whole."
  [& args] `(behavior ~@args))

(defmacro provided
  "A macro for using a Midje-style provided clause within any testing framework.
   This macro rewrites assertion-style mocking statements into code that can do that mocking.
   See the clojure.spec for `::p/mocks`.
   See the doc string for `p/parse-arrow-count`."
  [string & forms]
  (p/provided* &env false string forms))

(defmacro when-mocking
  "A macro that works just like 'provided', but requires no string and outputs no extra text in the test output.
   See the clojure.spec for `::p/mocks`.
   See the doc string for `p/parse-arrow-count`."
  [& forms]
  (p/provided* &env false :skip-output forms))

(defmacro provided!
  "Just like `provided`, but forces mocked functions to conform to the spec of the original function (if available)."
  [description & forms]
  (p/provided* &env true description forms))

(defmacro when-mocking!
  "Just like when-mocking, but forces mocked functions to conform to the spec of the original function (if available)."
  [& forms]
  (p/provided* &env true :skip-output forms))

(s/fdef assertions :args ::ae/assertions)
(defmacro assertions [& forms]
  (let [blocks  (ae/fix-conform (fss/conform! ::ae/assertions forms))
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

(defmacro generated-stub
  "Returns a function that can behave just like `f` (which must be a symbol), but that will simply verify
  arguments according to its `:args` spec and then return a value that conforms to that function's `:ret` spec."
  [f]
  (let [generate (im/if-cljs &env 'cljs.spec.gen.alpha/generate 'clojure.spec.gen.alpha/generate)
        gen      (im/if-cljs &env 'cljs.spec.alpha/gen 'clojure.spec.alpha/gen)]
    `(-> ~f ~gen ~generate)))
