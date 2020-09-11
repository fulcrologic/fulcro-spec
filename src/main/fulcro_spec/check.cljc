(ns fulcro-spec.check
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]))

(defn checker? [x] (and (fn? x) (::checker (meta x))))

(defn check-expr [msg [_ checker actual]]
  `(let [checker# ~checker, actual# ~actual
         location# ~(select-keys (meta checker) [:line])]
     (when-not (checker? checker#)
       (throw (ex-info "checker should be annotated with ^::checker or created with checker macro" {:checker checker# :meta (meta checker#)})))
     (when-let [failures# ((all* checker#) actual#)]
       (doseq [f# failures#]
         (t/do-report
           (merge {:message ~msg} f#
             {:type :fail}
             location#))))))

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
    (filter check-failure?
      (flatten
        (map #(% actual) checkers)))))

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

(defn exists?* [msg]
  (checker [actual]
    (when-not (some? actual)
      {:message msg
       :expected `some?
       :actual actual})))

(defn every?* [& checkers]
  (checker [actual]
    (conj (mapcat (apply all* checkers) actual)
      ((is?* seq) actual))))

(defn in* [path & checkers]
  (checker [actual]
    ((apply all*
       (exists?* (str "expected " path " to exist in " actual))
       checkers)
     (get-in actual path))))

(defn embeds?*
  ([expected] (embeds?* expected []))
  ([expected path]
   (checker [actual]
     (seq
       (for [[k v] expected]
         (let [actual-value (get actual k)
               path (conj path k)]
           (cond
             (map? v) #_=> ((embeds?* v path) actual-value)
             (checker? v) #_=> (v actual-value)
             (fn? v) (throw (ex-info "function found, should be annotated with ^::checker or created with checker macro" {:function v :meta (meta v)}))
             (not= actual-value v)
             #_=> {:actual actual-value
                   :expected v
                   :message (str "at path " path ":")})))))))
