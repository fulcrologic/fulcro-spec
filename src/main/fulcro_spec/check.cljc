(ns fulcro-spec.check
  #?(:cljs (:require-macros [fulcro-spec.check :refer [checker]]))
  (:require
    #?@(:cljs [[goog.string.format]
               [goog.string :refer [format]]])
    [clojure.spec.alpha :as s]
    [clojure.test :as t]))

(defn checker? [x] (and (fn? x) (::checker (meta x))))

(defn check-expr [msg [_ checker actual]]
  `(let [checker# ~checker, actual# ~actual, msg# ~msg
         location# ~(select-keys (meta checker) [:line])]
     (when-not (checker? checker#)
       (throw (ex-info "checker should be created with `checker` macro"
                {:checker checker# :meta (meta checker#)})))
     (if-let [failures# ((all* checker#) actual#)]
       (doseq [f# failures#]
         (t/do-report (merge {:message msg#} f# {:type :fail} location#)))
       (t/do-report (merge {:type :pass :message msg#} location#)))))

(defmacro checker [arglist & args]
  (assert (= 1 (count arglist))
    "A checker arglist should only have one argument.")
  `(with-meta (fn ~arglist ~@args) {::checker true}))

(defn check-failure? [x]
  (and (map? x)
    (or
      (contains? x :expected)
      (contains? x :actual)
      (contains? x :message))))

(defn all* [& checkers]
  (checker [actual]
    (->> checkers
      (map #(% actual))
      (flatten)
      (filter check-failure?)
      seq)))

(defn is?* [predicate]
  (checker [actual]
    (when-not (predicate actual)
      {:actual actual
       :expected predicate})))

(defn equals?* [expected]
  (checker [actual]
    (when-not (= expected actual)
      {:actual actual
       :expected expected})))

(defn valid?* [spec]
  (checker [actual]
    (when-not (s/valid? spec actual)
      {:message (s/explain-str spec actual)
       :actual actual
       :expected spec})))

(defn re-find?* [regex]
  (checker [actual]
    (when-not (re-find regex actual)
      {:message (format "Failed to find `%s` in '%s'" regex actual)
       :actual actual
       :expected `(re-pattern ~(str regex))})))

(defn seq-matches?* [coll]
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

(defn exists?* [msg]
  (checker [actual]
    (when-not (some? actual)
      {:message msg
       :expected `some?
       :actual actual})))

(defn every?* [& checkers]
  (checker [actual]
    (if (seqable? actual)
      (mapcat (apply all* checkers) actual)
      ((is?* seqable?) actual))))

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

(defn in* [path & checkers]
  (checker [actual]
    (if-let [nested (get-in actual path)]
      ((apply all* checkers) nested)
      (let [missing-path (path-to-get-in-failure actual path)
            failing-path-segment (last missing-path)
            failing-path (vec (drop-last missing-path))]
        {:actual actual
         :expected `(in* ~missing-path)
         :message (format "expected `%s` to contain `%s` at path %s"
                     (pr-str (get-in actual failing-path))
                     failing-path-segment
                     (pr-str failing-path))}))))

(defn embeds?* [expected]
  (letfn [(-embeds?* [expected path]
            (checker [actual]
              (for [[k v] expected]
                (let [actual-value (get actual k ::not-found)
                      path (conj path k)]
                  (cond
                    (map? v) #_=> ((-embeds?* v path) actual-value)
                    (checker? v) #_=> (v actual-value)
                    (fn? v) (throw (ex-info "function found, should be created with `checker` macro"
                                     {:function v :meta (meta v)}))
                    (= actual-value ::not-found)
                    #_=> {:actual actual
                          :expected v
                          :message (str "at path " (vec (drop-last path)) ":")}
                    (not= actual-value v)
                    #_=> {:actual actual
                          :expected v
                          :message (str "at path " path ":")})))))]
    (-embeds?* expected [])))
