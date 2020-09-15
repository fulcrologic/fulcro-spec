(ns fulcro-spec.check
  #?(:cljs (:require-macros [fulcro-spec.check :refer [checker]]))
  (:require
    #?@(:cljs [[goog.string.format]
               [goog.string :refer [format]]])
    [clojure.spec.alpha :as s]
    [clojure.test :as t]))

(defn checker?
  "Checks if passed in argument was a function created by the `checker` macro.
   NOTE: the fact that a checker is just a fn with metadata is an internal detail, do not rely on that fact, and prefer usage of the `checker` macro."
  [x] (and (fn? x) (::checker (meta x))))

(defn prepend-message
  "WARNING: INTERNAL HELPER, DO NOT USE!"
  [msg fail]
  (if-not (:message fail)
    (assoc fail :message msg)
    (update fail :message (partial str msg "\n"))))

(defn check-expr [msg [_ checker actual]]
  `(let [checker# ~checker, actual# ~actual, msg# ~msg
         location# ~(select-keys (meta checker) [:line])]
     (if-let [failures# ((all* checker#) actual#)]
       (doseq [f# failures#]
         (t/do-report (merge (prepend-message msg# f#) {:type :fail} location#)))
       (t/do-report (merge {:type :pass :message msg#} location#)))))

(defmacro checker
  "Creates a function that takes only one argument (usually named `actual`).
   For use in =check=> assertions.
   NOTE: the fact that a checker is just a fn with metadata is an internal detail, do not rely on that fact, and prefer usage of this `checker` macro."
  [arglist & args]
  (assert (= 1 (count arglist))
    "A checker arglist should only have one argument.")
  `(with-meta (fn ~arglist ~@args) {::checker true}))

(defn check-failure? [x]
  (and (map? x)
    (or
      (contains? x :expected)
      (contains? x :actual)
      (contains? x :message))))

(defn all*
  "Takes one or more `checker`s, and returns a new `checker`.
   That checker takes an `actual` argument, and calls every checker with it as the first and only argument.
   Returns nil or a sequence of maps matching `check-failure?`."
  [c & cs]
  (doseq [c (cons c cs)]
    (when-not (checker? c)
      (throw (ex-info "checker should be created with `checker` macro"
               {:checker c :meta (meta c)}))))
  (checker [actual]
    (->> (cons c cs)
      (map #(% actual))
      (flatten)
      (filter check-failure?)
      seq)))

(defn is?*
  "Takes any truthy predicate function and returns a checker that checks with said predicate."
  [predicate]
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

(defn valid?*
  "Takes any valid clojure.spec.alpha/spec and returns a checker.
   The checker checks with `s/valid?` and calls `s/explain-str` for the `:message`."
  [spec]
  (checker [actual]
    (when-not (s/valid? spec actual)
      {:message (s/explain-str spec actual)
       :actual actual
       :expected spec})))

(defn re-find?*
  "Takes a regex (or string) and returns a checker that checks using `re-find`."
  [regex]
  (checker [actual]
    (when-not (re-find regex actual)
      {:message (format "Failed to find `%s` in '%s'" regex actual)
       :actual actual
       :expected `(re-pattern ~(str regex))})))

(defn seq-matches?*
  "Takes a `sequential?` collection, and returns a checker that checks its argument in a sequential manner, ie:
   `(seq-matches?* [1 2]) =check=> [1 2]`
   Can also take checkers as values, eg:
   `(seq-matches?* [(is?* pos?)]) =check=> [42]`"
  [coll]
  (assert (sequential? coll)
    "seq-matches?* can only take `sequential?` collections, eg: lists & vectors")
  (all*
    (checker [actual]
      (assert (sequential? actual)
        "seq-matches?* can only compare against `sequential?` collections, eg: lists & vectors")
      (for [[idx act exp] (map vector (range) actual coll)]
        (cond
          (checker? exp) (exp act)
          (fn? exp) (throw (ex-info "function found, should be created with `checker` macro"
                             {:function exp :meta (meta exp)}))
          (not= act exp) {:actual act :expected exp
                          :message (format "at index `%s` failed to match:" idx)})))))

(defn exists?*
  "Takes a failure message and returns a checker that checks for non-nil values."
  [msg]
  (checker [actual]
    (when-not (some? actual)
      {:message msg
       :expected `some?
       :actual actual})))

(defn every?*
  "Takes one or more checkers, and returns a checker that runs all checker arguments on each element of the checker argument sequence."
  [c & cs]
  (checker [actual]
    (assert (seqable? actual)
      "every?* can only take `seqable?` collections, ie: responds to `seq`")
    (mapcat (apply all* c cs) actual)))

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

(defn in*
  "Takes a path and one or more checkers, and returns a checker that focuses the checker argument on the result of running `(get-in actual path)`.
   If the call to `get-in` failed it returns a `check-failure?`."
  [path c & cs]
  (checker [actual]
    (if-let [nested (get-in actual path)]
      ((apply all* c cs) nested)
      (let [missing-path (path-to-get-in-failure actual path)
            failing-path-segment (last missing-path)
            failing-path (vec (drop-last missing-path))]
        {:actual actual
         :expected `(in* ~missing-path)
         :message (format "expected `%s` to contain `%s` at path %s"
                     (pr-str (get-in actual failing-path))
                     failing-path-segment
                     (pr-str failing-path))}))))

(defn append-message
  "WARNING: INTERNAL HELPER, DO NOT USE!"
  [message failures]
  (some->> failures
    (map #(update % :message
            (fn [msg]
              (if-not msg message
                (str msg "\n" message)))))))

(defn embeds?*
  "Takes a map and returns a checker that checks if the values at the keys match (using `=`) .
   The map values can be `checker`s, but will throw an error if passed a raw function.
   The map can be arbitrarily nested with further maps, eg:
   ```
   (embeds?* {:a 1, :b {:c (is?* even?)}}) =check=> {:a 1, :b {:c 2}}
   ```"
  [expected]
  (assert (map? expected)
    "embeds?* can only take `map?`s.")
  (letfn [(-embeds?* [expected path]
            (checker [actual]
              (for [[k v] expected]
                (let [value-at-key (get actual k ::not-found)
                      path (conj path k)]
                  (cond
                    (map? v) #_=> ((-embeds?* v path) value-at-key)
                    (checker? v) #_=> (append-message (format "at path %s:" path)
                                        ((all* v) value-at-key))
                    (fn? v) (throw (ex-info "function found, should be created with `checker` macro"
                                     {:function v :meta (meta v)}))
                    (= value-at-key ::not-found)
                    #_=> {:actual actual
                          :expected v
                          :message (str "at path " (vec (drop-last path)) ":")}
                    (not= value-at-key v)
                    #_=> {:actual (get actual k)
                          :expected v
                          :message (str "at path " path ":")})))))]
    (-embeds?* expected [])))
