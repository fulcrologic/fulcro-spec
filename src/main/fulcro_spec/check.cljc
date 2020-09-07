(ns fulcro-spec.check
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]))

(defn check-expr [msg [_ checker actual]]
  `(let [location# ~(select-keys (meta checker) [:line])]
     (when-let [failures# ((all* ~checker) ~actual)]
       (doseq [f# failures#]
         (t/do-report
           (merge {:message ~msg} f#
             {:type :fail}
             location#))))))

(defn check-failure? [x]
  (and (map? x)
    (or
      (contains? x :expected)
      (contains? x :actual)
      (contains? x :message))))

(defn all* [& checkers]
  (fn [actual]
    (filter check-failure?
      (flatten
        (map #(% actual) checkers)))))

(defn is?* [predicate]
  (fn [actual]
    (when-not (predicate actual)
      {:actual actual
       :expected predicate})))

(defn equals?* [expected]
  (fn [actual]
    (when-not (= expected actual)
      {:actual actual
       :expected expected})))

(defn valid?* [spec]
  (fn [actual]
    (when-not (s/valid? spec actual)
      {:message (s/explain-str spec actual)
       :actual actual
       :expected spec})))

(defn exists?* [msg]
  (fn [actual]
    (when-not (some? actual)
      {:message msg
       :expected `some?
       :actual actual})))

(defn every?* [& checkers]
  (fn [actual]
    (conj (mapcat (apply all* checkers) actual)
      ((is?* seq) actual))))

(defn in* [path & checkers]
  (fn [actual]
    ((apply all*
       (exists?* (str "expected " path " to exist in " actual))
       checkers)
     (get-in actual path))))

(defn embeds?*
  ([expected] (embeds?* expected []))
  ([expected path]
   (fn [actual]
     (seq
       (for [[k v] expected]
         (let [actual-value (get actual k)
               path (conj path k)]
           (cond
             (map? v) #_=> ((embeds?* v path) actual-value)
             (not= actual-value v)
             #_=> {:actual actual-value
                   :expected v
                   :message (str "at path " path ":")})))))))
