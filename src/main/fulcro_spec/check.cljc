(ns fulcro-spec.check
  #?(:cljs (:require-macros [fulcro-spec.check :refer [checker assert-is!]]))
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [fulcro-spec.impl.check :as impl]))

(defmacro checker
  "Creates a function that takes only one argument (usually named `actual`).
   For use in =check=> assertions.
   NOTE: the fact that a checker is just a fn with metadata is an internal detail, do not rely on that fact, and prefer usage of this `checker` macro."
  [arglist & args]
  (assert (= 1 (count arglist))
    "A checker arglist should only have one argument.")
  `(with-meta (fn ~arglist ~@args) {::checker true}))

(defn checker?
  "Checks if passed in argument was a function created by the `checker` macro.
   NOTE: the fact that a checker is just a fn with metadata is an internal detail, do not rely on that fact, and prefer usage of the `checker` macro."
  [x] (and (fn? x) (::checker (meta x))))

(defn assert-are-checkers!
  "Helper to ensure that checker \"combinators\" are passed checkers.
   The tag can be anything, but by convention should be the namespaced qualified symbol of the checker this function is inside of.
   eg: `(assert-are-checkers! `and* checkers)`"
  [tag checkers]
  (doseq [c checkers]
    (when-not (checker? c)
      (throw (ex-info (impl/format-str "Invalid checker `%s` passed to `%s`"
                        (pr-str c) tag)
               {:checker c :meta (meta c) :type (type c)})))))

(defmacro assert-is!
  "Helper macro to assert that values satisfy a predicate.
   The tag can be anything, but by convention should be the namespaced qualified symbol of the checker this function is inside of.
   eg: `(assert-is! `is?* ifn? predicate)`"
  [tag pred value]
  `(let [value# ~value]
     (when-not (~pred value#)
       (throw (ex-info (impl/format-str "Invalid argument `%s` to `%s`, failed predicate `%s`"
                         (pr-str value#) ~tag '~pred)
                {:tag ~tag :predicate (str ~pred) :value value#})))))

(defn- check-failure? [x]
  (and (map? x)
    (or
      (contains? x :expected)
      (contains? x :actual)
      (contains? x :message)
      (contains? x :type))))

(defn all*
  "Takes one or more `checker`s, and returns a new `checker`.
   That checker takes an `actual` argument, and calls every checker with it as the first and only argument.
   Returns nil or a sequence of maps matching `check-failure?`."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `all* checkers)
    (checker [actual]
      (->> (cons c cs)
        (map #(% actual))
        (flatten)
        (filter check-failure?)
        seq))))

(defn and*
  "Takes one or more `checker`s, and returns a new `checker`.
   That checker takes an `actual` argument, and calls each checker in succession, short circuiting if any returned a `check-failure?`.
   Returns nil or a sequence of maps matching `check-failure?`."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `and* checkers)
    (checker [actual]
      (loop [c (first checkers)
             cs (rest checkers)]
        (if (nil? c) nil
          (let [?fs (c actual)
                ?failures (if (sequential? ?fs)
                            (flatten ?fs)
                            (vector ?fs))]
            (or (seq (filter check-failure? ?failures))
              (recur (first cs) (rest cs)))))))))

(defn fmap*
  "Creates a new checker that is the result of applying the function to the checker arguments before passing it to the wrapped checker.
   Eg: `[:b :c :a] =check=> (fmap* sort (equals?* [:a :b :c]))`"
  [f c]
  (assert-are-checkers! `fmap* [c])
  (assert-is! `fmap* ifn? f)
  (checker [actual]
    (c (f actual))))

(defn with-message*
  "Appends the message to all failures that the checker may return.
   eg: `2.0 =check=> (_/with-message* \"some info\" (_/is?* double?))`"
  [message c & cs]
  (assert-is! `with-message* string? message)
  (let [checkers (cons c cs)]
    (assert-are-checkers! `with-message* checkers)
    (checker [actual]
      (map (partial impl/prepend-message message)
        ((apply all* checkers) actual)))))

(defn behavior*
  "NOTE: experimental, may be considered poor form and deprecated at a later date.
   Equivalent to `fulcro-spec.core/behavior`, so you can describe the expected behavior being checked.
   eg: `2.0 =check=> (_/behavior* \"is a double\" (_/is?* double?))`"
  [string c & cs]
  (assert-is! `behavior* string? string)
  (let [checkers (cons c cs)]
    (assert-are-checkers! `behavior* checkers)
    (checker [actual]
      [{:type :begin-behavior :string string}
       (or (seq ((apply all* checkers) actual))
         {:type :pass})
       {:type :end-behavior :string string}])))

(defn is?*
  "Takes any truthy predicate function and returns a checker that checks with said predicate."
  [predicate]
  (assert-is! `is?* ifn? predicate)
  (checker [actual]
    (when-not (predicate actual)
      {:actual actual
       :expected predicate})))

(defn equals?*
  "Takes any value and returns a checker that checks for equality."
  [expected]
  (checker [actual]
    (when-not (= expected actual)
      {:actual actual
       :expected expected})))

(defn- specable? [x] (or (ifn? x) (s/spec? x)))

(defn valid?*
  "Takes any valid clojure.spec.alpha/spec and returns a checker.
   The checker checks with `s/valid?` and calls `s/explain-str` for the `:message`."
  [spec]
  (assert-is! `valid?* specable? spec)
  (checker [actual]
    (when-not (s/valid? spec actual)
      {:message (s/explain-str spec actual)
       :actual actual
       :expected spec})))

(defn- regex? [x]
  (instance?
    #?(:clj java.util.regex.Pattern
       :cljs js/RegExp)
    x))

(defn- regexable? [x]
  (or (string? x)
    (regex? x)))

(defn re-find?*
  "Takes a regex (or string) and returns a checker that checks using `re-find`.
   NOTE: Will first check that the incoming data (`actual`) is a `string?`"
  [regex]
  (assert-is! `re-find?* regexable? regex)
  (and*
    (is?* string?)
    (checker [actual]
      (when-not (re-find regex actual)
        {:message (impl/format-str "Failed to find `%s` in '%s'" regex actual)
         :actual actual
         :expected `(re-pattern ~(str regex))}))))

(defn seq-matches?*
  "Takes a `sequential?` collection, and returns a checker that checks its argument in a sequential manner, ie:
   `(seq-matches?* [1 2]) =check=> [1 2]`

   NOTE: does **NOT** check if the `actual` collection contains more or fewer items than expected! If not desireable, use `seq-matches-exactly?*` instead.

   Can also take checkers as values, eg:
   `(seq-matches?* [(is?* pos?)]) =check=> [42]`"
  [expected]
  (assert-is! `seq-matches?* sequential? expected)
  (and*
    (is?* sequential?)
    (checker [actual]
      (for [[idx act exp] (map vector (range) (concat actual (repeat ::not-found)) expected)]
        (cond
          (checker? exp) (exp act)
          (fn? exp) (throw (ex-info "function found, should be created with `checker` macro"
                             {:function exp :meta (meta exp)}))
          (not= act exp) {:actual act :expected exp
                          :message (impl/format-str "at index `%s` failed to match:" idx)})))))

(defn- min<=max [{:keys [min-len max-len]}] (<= min-len max-len))

(defn of-length?*
  "Checks that given `seqable?` collection has the expected length.
   Has two arities:
   - The first checks that the actual length is equal to the `expected-length`.
   - The second, takes two numbers and verifies that the actual length is between (inclusively) the two numbers."
  ([expected-length]
   (assert-is! `of-length?* int? expected-length)
   (and*
     (is?* seqable?)
     (checker [actual]
       (let [length (count actual)]
         (when-not (= expected-length length)
           {:actual actual
            :expected `(~'of-length?* :equal-to ~expected-length)
            :message (impl/format-str "Expected collection count to be %s was %s"
                       expected-length length)})))))
  ([min-len max-len]
   (assert-is! `of-length?* int? min-len)
   (assert-is! `of-length?* int? max-len)
   (assert-is! `of-length?* min<=max {:min-len min-len :max-len max-len})
   (and*
     (is?* seqable?)
     (checker [actual]
       (let [length (count actual)]
         (when-not (<= min-len length max-len)
           {:actual actual
            :expected `(~'of-length?* :between :min ~min-len :max ~max-len)
            :message (impl/format-str "Expected collection count %s to be between [%s,%s]"
                       length min-len max-len)}))))))

(defn seq-matches-exactly?*
  "Takes a `sequential?` collection, and returns a checker that checks its argument in a sequential manner.
   NOTE: \"exactly\" means that the `actual` collection is checked to have the **exact** same length as the `expected` collection.
   Eg:
   `(seq-matches-exactly?* [1 2]) =check=> [1 2]`
   Can also take checkers as values, eg:
   `(seq-matches-exactly?* [(is?* pos?)]) =check=> [42]`"
  [expected]
  (assert-is! `seq-matches-exactly?* sequential? expected)
  (and*
    (is?* sequential?)
    (of-length?* (count expected))
    (seq-matches?* expected)))

(defn exists?*
  "Takes an optional failure message and returns a checker that checks for non-nil values."
  [& [msg]]
  (when msg (assert-is! `exists?* string? msg))
  (checker [actual]
    (when-not (some? actual)
      (cond-> {:expected `some?
               :actual actual}
        msg (assoc :message msg)))))

(defn every?*
  "Takes one or more checkers, and returns a checker that runs all checker arguments on each element of the checker argument sequence.
   WARNING: checks every item in the `actual` sequence, unlike `every?`.
   WARNING: will pass if given an empty sequence, like `every?`.
   If not desireable, compose `(is?* seq)` or `(of-length? ...)` before this checker.
   NOTE: will short circuit execution of expected checkers using the same semantics as `and*`."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `every?* checkers)
    (and*
      (is?* seqable?)
      (checker [actual]
        (mapcat (apply and* checkers) actual)))))

(defn subset?*
  "Takes an `expected` set, and will check that `actual` contains only a subset of what is expected.
   Eg:
   ```
   ; PASSES
   #{:a} =check=> (subset?* #{:a :b})

   ; FAILS
   #{:a :b :c} =check=> (subset?* #{:a :b})
   ```"
  [expected]
  (assert-is! `subset?* set? expected)
  (and*
    (is?* seqable?)
    (checker [actual]
      (when-not (set/subset? (set actual) expected)
        {:actual {:extra-values (set/difference (set actual) expected)}
         :expected `(~'subset?* ~expected)
         :message "Found extra values in set"}))))

(defn- path-to-get-in-failure [value path]
  (->> path
    (reduce
      (fn [{:as acc :keys [path value]} path-segment]
        (if-not (some? value)
          (reduced {:path path})
          (-> acc
            (update :path conj path-segment)
            (update :value get path-segment))))
      {:path [] :value value})
    :path))

(defn- add-path* [path ?failures]
  (some->> ?failures
    (map #(update % ::path
            (fn [prev-path]
              (vec (concat path (or prev-path []))))))))

(defn in*
  "Takes a path and one or more checkers, and returns a checker that focuses the checker argument on the result of running `(get-in actual path)`.
   If the call to `get-in` failed it returns a `check-failure?`."
  [path c & cs]
  (assert-is! `in* vector? path)
  (let [checkers (cons c cs)]
    (assert-are-checkers! `in* checkers)
    (checker [actual]
      (if-let [nested (get-in actual path)]
        (add-path* path ((apply all* checkers) nested))
        (let [missing-path (path-to-get-in-failure actual path)
              failing-path-segment (last missing-path)
              failing-path (vec (drop-last missing-path))]
          {:actual actual
           :expected `(in* ~missing-path)
           :message (impl/format-str "expected `%s` to contain `%s` at path %s"
                      (pr-str (get-in actual failing-path))
                      failing-path-segment
                      (pr-str failing-path))})))))

(defn embeds?*
  "Takes a map and returns a checker that checks if the values at the keys match (using `=`) .
   The map values can be `checker`s, but will throw an error if passed a raw function.
   The map can be arbitrarily nested with further maps, eg:
   ```
   (embeds?* {:a 1, :b {:c (is?* even?)}}) =check=> {:a 1, :b {:c 2}}
   ```
   Can also check that a key value pair was not found by checking for equality with ::not-found, eg:
   ```
   (embeds?* {:a ::not-found}) =check=> {}
   ```"
  [expected]
  (assert-is! `embeds?* map? expected)
  (letfn [(-embeds?* [expected path]
            (checker [actual]
              (for [[k v] expected]
                (let [value-at-key (get actual k ::not-found)
                      path (conj path k)]
                  (cond
                    (map? v)
                    #_=> (if-not (map? value-at-key)
                           {:actual value-at-key
                            :expected v
                            ::path path}
                           ((-embeds?* v path) value-at-key))
                    (checker? v) #_=> (add-path* path
                                        ((all* v) value-at-key))
                    (fn? v) (throw (ex-info "Expected a checker, found a function instead!"
                                     {:function v :meta (meta v)}))
                    (and (= value-at-key ::not-found)
                      (not= v ::not-found))
                    #_=> {:actual ::not-found
                          :expected v
                          ::path path}
                    (not= value-at-key v)
                    #_=> {:actual value-at-key
                          :expected v
                          ::path path})))))]
    (all* (-embeds?* expected []))))

(defn throwable*
  "Checks that the `actual` value is a `Throwable` (or in cljs a `js/Error`).
   If successful, passes the `Throwable` to `all*` passed in checkers."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `throwable* checkers)
    (checker [actual]
      (if (instance? #?(:clj Throwable :cljs js/Error) actual)
        ((apply all* checkers) actual)
        {:actual actual
         :expected #?(:clj Throwable :cljs js/Error)}))))

(defn exception*
  "Checks that the `actual` value is an `Exception` (or in cljs a `js/Error`).
   If successful, passes the `Exception` to `all*` passed in checkers."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `exception* checkers)
    (checker [actual]
      (if (instance? #?(:clj Exception :cljs js/Error) actual)
        ((apply all* checkers) actual)
        {:actual actual
         :expected #?(:clj Exception :cljs js/Error)}))))

(defn ex-data*
  "Checks that the `actual` value is an `ExceptionInfo`.
  If successful, passes the `ex-data` to `all*` passed in checkers."
  [c & cs]
  (let [checkers (cons c cs)]
    (assert-are-checkers! `ex-data* checkers)
    (exception*
      (checker [actual]
        (if-let [data (some-> actual ex-data)]
          ((apply all* checkers) data)
          {:actual   actual
           :expected ex-data})))))
