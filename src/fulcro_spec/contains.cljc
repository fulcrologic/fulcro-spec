(ns fulcro-spec.contains
  (:require
    [clojure.set :refer [subset?]]
    [fulcro-spec.spec :as us]))

(defn todo []
  (throw (ex-info "todo / not-implemented" {})))

(defn ->kw-type [x]
  (cond
    (string? x) :string (us/regex? x) :regex
    (map? x)    :map    (set? x)      :set
    (list? x)   :list   (vector? x)   :list))

(defmulti -contains?
  (fn [& xs] (->> xs drop-last (mapv ->kw-type))))

(defmethod -contains? [:string :string] [act exp & [opt]]
  (re-find (re-pattern exp) act))

(defmethod -contains? [:string :regex] [act exp & [opt]]
  (re-find exp act))

(defmethod -contains? [:map :map] [act exp & [opt]]
  (subset? (set exp) (set act)))

(defmethod -contains? [:map :list] [act exp & [opt]]
  (case opt;cond-> ?
    :keys (subset? (set exp) (set (keys act)))
    :vals (subset? (set exp) (set (vals act)))
    false))

(defmethod -contains? [:set :set] [act exp & [opt]] ;1+
  (some exp act))
(defmethod -contains? [:set :list] [act exp & [opt]] ;all
  (subset? (set exp) act))

(defmethod -contains? [:list :set] [act exp & [opt]] ;1+
  (some exp act))
(defmethod -contains? [:list :list] [act exp & [opt]] ;all
  (case opt
    :gaps
    (todo)

    :any-order
    (todo)

    :both
    (todo)

    (let [pad (fn [p s] (str p s p))
          strip (fn [s] (->> s str drop-last rest (apply str)))
          re (->> exp strip (pad " "))]
      (re-find (re-pattern re) (->> act strip (pad " "))))))

(defmethod -contains? :default [exp act] (todo))

(defn *contains?
  ([exp] (fn [act] (*contains? act exp nil)))
  ([exp opt] (fn [act] (*contains? act exp opt)))
  ([act exp opt] (-contains? act exp opt)))
