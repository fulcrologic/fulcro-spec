(ns fulcro-spec.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha]
    [clojure.string :as str]
    [clojure.test]
    [fulcro-spec.assertions :as ae]
    [fulcro-spec.async :as async]
    [fulcro-spec.coverage :as coverage]
    [fulcro-spec.hooks :refer [hooks]]
    [fulcro-spec.impl.macros :as im]
    [fulcro-spec.proof :as proof]
    [fulcro-spec.provided :as p]
    [fulcro-spec.signature :as sig]
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
  (symbol (str "__" (str/replace s #"[^\w\d\-\!\#\$\%\&\*\_\<\>\?\|]" "-") "__")))

(defn- normalize-key
  "Normalizes a covers key to a symbol.
   When using backtick in macro calls like {:covers {`foo sig}}, the reader
   produces (quote ns/foo) as a list, not a symbol. This function extracts
   the symbol from such quote forms."
  [k]
  (if (and (seq? k) (= 'quote (first k)))
    (second k)                                              ; Extract symbol from (quote sym)
    k))                                                     ; Already a symbol

(defn- covers-map-code
  "Creates CODE (not data) that evaluates to a map where keys are symbols.
   This allows values to be evaluated at runtime while keys stay as symbols.

   Handles both:
   - Plain symbols: {my.ns/foo sig-var}
   - Quoted symbols from backtick: {(quote my.ns/foo) sig-var}

   Example output:
   (hash-map (quote my.ns/foo) sig-var)
   which at runtime evaluates to {my.ns/foo \"abc123\"}"
  [covers]
  ;; Build (hash-map (quote k1) v1 (quote k2) v2 ...)
  ;; Normalize keys to handle (quote ...) forms from backtick
  (cons 'clojure.core/hash-map
    (mapcat (fn [[k v]]
              [(list 'quote (normalize-key k)) v])
      covers)))

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
     (specification {:covers {`my.ns/fn-under-test \"a1b2c3\"}} \"test name\" ...)

   An optional metadata map can appear before the test name to add custom metadata to the test var.
   Keyword selectors (like :focus) can follow the test name and are converted to metadata (e.g., {:focus true}).
   Both metadata sources are merged, with selector keywords taking precedence over the metadata map.

   The :covers key in metadata declares which functions this test covers and their sealed signatures.
   This enables transitive test coverage verification with staleness detection.

   Format: {:covers {fn-symbol \"signature\"}}
     - fn-symbol: Quoted symbol of the function being tested
     - signature: The function's signature (see fulcro-spec.signature/signature)

   Get signatures using:
     (fulcro-spec.signature/signature 'my.ns/fn scope-ns-prefixes)

   The signature function automatically returns:
     - Single-field \"xxxxxx\" for leaf functions (no in-scope callees)
     - Two-field \"xxxxxx,yyyyyy\" for non-leaf functions (has callees)

   Legacy format (no staleness detection): {:covers [`fn-a `fn-b]}

   Auto-skip support:
   When -Dfulcro-spec.auto-skip=true is set and all covered functions have
   matching signatures, the test body is replaced with a simple passing
   assertion. For fast full-suite runs, also set -Dfulcro-spec.sigcache=true"
  [& args]
  (let [{:keys [metadata name selectors body]} (fss/conform! ::specification args)
        covers              (:covers metadata)
        selector-meta       (zipmap selectors (repeat true))
        ;; Remove :covers from metadata - it's not a var metadata key
        combined-meta       (merge (dissoc metadata :covers) selector-meta)
        test-name           (-> (var-name-from-string name)
                              (with-meta combined-meta))
        prefix              (im/if-cljs &env "cljs.test" "clojure.test")
        form-meta           (select-keys (meta &form) [:line])
        hook-info           {::specification name
                             ::location      form-meta
                             ::covers        covers}
        ;; Build qualified test name for registration
        ns-sym              (if (im/cljs-env? &env)
                              `(symbol (namespace ::x))
                              `(symbol (str *ns*)))
        qualified-test-name (symbol (str *ns*) (str test-name))
        ;; Check if we should emit skip-check code (CLJ only, map covers)
        emit-skip-check?    (and (not (im/cljs-env? &env))
                              (map? covers)
                              (seq covers))]
    `(do
       ;; Register coverage at load time
       ~(when (seq covers)
          (im/if-cljs &env
            nil ; Skip coverage registration in CLJS (proof system is CLJ-only)
            (if (map? covers)
              ;; New format: {fn-sym "signature"}
              ;; Use covers-map-code to quote keys but allow values to be evaluated
              `(doseq [[fn-sym# sig#] ~(covers-map-code covers)]
                 (coverage/register-coverage! '~qualified-test-name fn-sym# sig#))
              ;; Legacy format: [fn-sym ...] (no signatures)
              `(doseq [fn-sym# ~(vec covers)]
                 (coverage/register-coverage! '~qualified-test-name fn-sym#)))))
       ;; Define the test
       (~(symbol prefix "deftest") ~test-name
         (im/with-reporting {:type      :specification
                             :string    ~name
                             :form-meta ~form-meta}
           ((:on-enter @hooks (fn [& _#])) ~hook-info)
           (let [result#
                 ~(if emit-skip-check?
                    ;; CLJ with map covers - add runtime skip check
                    ;; Use covers-map-code to quote keys but allow values to be evaluated
                    `(if (sig/already-checked? ~(covers-map-code covers) (:scope-ns-prefixes (proof/get-config)))
                       (do
                         (clojure.test/testing "skipped. Unchanged since last run"
                           (clojure.test/is true))
                         true)
                       (im/try-report ~name ~@body))
                    ;; CLJS or no covers - just run the body
                    `(im/try-report ~name ~@body))]
             ((:on-leave @hooks (fn [& _#])) ~hook-info)
             result#))))))

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

(defmacro when-mocking!!
  "Like when-mocking!, but also verifies that all mocked functions have transitive test coverage.
   This enforces that you're building a complete proof chain.

   Uses global config from `proof/configure!`. Optionally accepts an options map as first argument
   to override global settings:
   - :scope-ns-prefixes - set of namespace prefix strings for coverage checking

   Examples:
     ;; Using global config (set via proof/configure!)
     (when-mocking!!
       (myapp.db/save! data) => {:id 1}
       (assertions
         (myapp.orders/create-order data) => {:id 1}))

     ;; With explicit options (overrides global config)
     (when-mocking!! {:scope-ns-prefixes #{\"myapp\"}}
       (myapp.db/save! data) => {:id 1}
       (assertions
         (myapp.orders/create-order data) => {:id 1}))"
  [& args]
  (let [[opts forms] (if (and (seq args) (map? (first args)))
                       [(first args) (rest args)]
                       [{} args])
        mocked-fns (p/extract-mocked-symbols forms)]
    `(do
       ;; At test runtime, verify each mocked fn has transitive coverage
       (doseq [fn-sym# '~mocked-fns]
         (proof/assert-transitive-coverage! fn-sym# ~opts))
       ;; Then run the normal spec-validating mocking
       ~(p/provided* &env true :skip-output forms))))

(defmacro provided!!
  "Like provided!, but also verifies that all mocked functions have transitive test coverage.

   First argument is the description string. Optionally accepts an options map as second argument
   to override global settings from `proof/configure!`:
   - :scope-ns-prefixes - set of namespace prefix strings for coverage checking

   Examples:
     ;; Using global config
     (provided!! \"database operations are mocked\"
       (myapp.db/save! data) => {:id 1}
       (assertions
         (myapp.orders/create-order data) => {:id 1}))

     ;; With explicit options
     (provided!! \"database operations are mocked\" {:scope-ns-prefixes #{\"myapp\"}}
       (myapp.db/save! data) => {:id 1}
       (assertions
         (myapp.orders/create-order data) => {:id 1}))"
  [description & args]
  (let [[opts forms] (if (and (seq args) (map? (first args)))
                       [(first args) (rest args)]
                       [{} args])
        mocked-fns (p/extract-mocked-symbols forms)]
    `(do
       ;; At test runtime, verify each mocked fn has transitive coverage
       (doseq [fn-sym# '~mocked-fns]
         (proof/assert-transitive-coverage! fn-sym# ~opts))
       ;; Then run the normal spec-validating mocking with description
       ~(p/provided* &env true description forms))))

(s/fdef assertions :args ::ae/assertions)
(defmacro assertions [& forms]
  (let [blocks  (ae/parse-assertions forms)
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
