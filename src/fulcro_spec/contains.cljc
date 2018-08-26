(ns fulcro-spec.contains
  (:require
    [clojure.set :refer [subset?]]
    [clojure.spec.alpha :as s]
    [fulcro-spec.spec :as fss]))

(comment
  (defn f [a] (+ 1 a))
  (s/fdef f
    :args (s/cat :a int?) :ret int?)

  (s/explain (:args (s/get-spec `f)) ["a"])

  (defn generated-stub [& incoming-args]
    (let [result 22]
      (when-let [spec (s/get-spec `f)]
        (let [{:keys [args ret fn]} spec]
          (when (and args (not (s/valid? args incoming-args)))
            (throw (ex-info (str "Mock of " `f " was sent arguments that do not conform to spec: " (with-out-str (s/explain args incoming-args))) {})))
          (when (and ret (not (s/valid? ret result)))
            (throw (ex-info (str "Mock of " `f " returned a value that does not conform to spec: " (with-out-str (s/explain ret result))) {})))))
      result))

  (generated-stub 11)
  )

(defn todo []
  (throw (ex-info "todo / not-implemented" {})))

(defn ->kw-type [x]
  (cond
    (string? x) :string (fss/regex? x) :regex
    (map? x) :map (set? x) :set
    (list? x) :list (vector? x) :list))

(defmulti -contains?
  (fn [& xs] (->> xs drop-last (mapv ->kw-type))))

(defmethod -contains? [:string :string] [act exp & [opt]]
  (re-find (re-pattern exp) act))

(defmethod -contains? [:string :regex] [act exp & [opt]]
  (re-find exp act))

(defmethod -contains? [:map :map] [act exp & [opt]]
  (subset? (set exp) (set act)))

(defmethod -contains? [:map :list] [act exp & [opt]]
  (case opt                                                 ;cond-> ?
    :keys (subset? (set exp) (set (keys act)))
    :vals (subset? (set exp) (set (vals act)))
    false))

(defmethod -contains? [:set :set] [act exp & [opt]]         ;1+
  (some exp act))
(defmethod -contains? [:set :list] [act exp & [opt]]        ;all
  (subset? (set exp) act))

(defmethod -contains? [:list :set] [act exp & [opt]]        ;1+
  (some exp act))
(defmethod -contains? [:list :list] [act exp & [opt]]       ;all
  (case opt
    :gaps
    (todo)

    :any-order
    (todo)

    :both
    (todo)

    (let [pad   (fn [p s] (str p s p))
          strip (fn [s] (->> s str drop-last rest (apply str)))
          re    (->> exp strip (pad " "))]
      (re-find (re-pattern re) (->> act strip (pad " "))))))

(defmethod -contains? :default [exp act] (todo))

(defn *contains?
  ([exp] (fn [act] (*contains? act exp nil)))
  ([exp opt] (fn [act] (*contains? act exp opt)))
  ([act exp opt] (-contains? act exp opt)))
