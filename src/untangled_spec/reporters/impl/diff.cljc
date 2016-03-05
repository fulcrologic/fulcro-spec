(ns untangled-spec.reporters.impl.diff
  (:require [clojure.set :as set]
            [clojure.walk :as walk]

            [#?(:clj clojure.test :cljs cljs.test)
             :refer [do-report]]))

(declare diff)
(def nf '..nothing..)

(defn diff-elem
  ([] [])
  ([exp got]
   [:+ exp :- got]))

(defn diff-elem? [?de]
  (and (vector? ?de)
       (= 4 (count ?de))
       (let [[p _ m _] ?de]
         (and (= p :+) (= m :-)))))

(defn diff? [?d]
  (and ?d (map? ?d)
       (every? vector (keys ?d))
       (every? diff-elem? (vals ?d))))

(defn extract [?d]
  (assert (and (vector? ?d) (= 2 (count ?d))))
  (assert (vector?    (first ?d)))
  (assert (diff-elem? (second ?d)))
  (let [[path [_ exp _ got]] ?d]
    {:path path :exp exp :got got}))

(defn map-keys [f m] (into {} (for [[k v] m] [(f k) v])))

(defn- map-diff [ks exp act]
  (loop [[k & ks] ks, exp exp, act act, path [], diffs {}]
    (if-not k diffs
      (let [ev (get exp k nf)
            av (get act k nf)]
        (if (and ev av (= ev av))
          (recur ks exp act path diffs)
          (let [d (diff ev av :recur)
                diffs (cond
                        (diff-elem? d)
                        (assoc diffs [k] d)
                        (diff? d) (map-keys #(vec (cons k %)) d)
                        :else (throw (ex-info "This should not have happened"
                                              {:d d :exp exp :act act})))]
            (recur ks exp act [] diffs)))))))

(defn- seq-diff [exp act]
  (loop [[i & is] (range), [e & es :as exp] exp, [a & as :as act] act, diffs {}]
    (cond
      (and (seq exp) (seq act) (not= e a))
      (let [d (diff e a :recur)
            diffs (cond
                    (diff-elem? d)
                    (assoc diffs [i] d)
                    (diff? d) (map-keys #(vec (cons i %)) d)
                    :else (throw (ex-info "This should not have happened"
                                          {:d d :exp exp :act act})))]
        (recur is es as diffs))

      (and (seq exp) (empty? act))
      (recur is es as (assoc diffs [i] (diff-elem e nf)))

      (and (empty? exp) (seq act))
      (recur is es as (assoc diffs [i] (diff-elem nf a)))

      (every? empty? [exp act]) diffs

      :else (recur is es as diffs))))

(defn set-diff [exp act]
  (let [missing-from-act (set/difference act exp)
        missing-from-exp (set/difference exp act)]
    (if (or (seq missing-from-act) (seq missing-from-exp))
      (diff-elem missing-from-exp missing-from-act)
      (diff-elem))))

(defn diff [exp act & [opt]]
  (let [recur? (#{:recur} opt)]
    (cond->
      (cond
        (every? map? [exp act])
        (map-diff (vec (set (mapcat keys [exp act])))
                  exp act)

        (every? string? [exp act])
        (diff-elem exp act)

        (every? set? [exp act])
        (cond->> (set-diff exp act)
          (not recur?) (assoc {} []))

        (every? sequential? [exp act])
        (seq-diff exp act)

        (not= (type exp) (type act))
        (diff-elem exp act)

        (every? coll? [exp act])
        (seq-diff exp act)

        ;; RECUR GUARD
        (not recur?) {}

        (not= exp act)
        (diff-elem exp act)

        :else [])
      (not recur?) (#(cond->> %
                       (diff-elem? %) (assoc {} []))))))

(defn patch [x diffs & [f]]
  (let [f (or f (comp :exp extract))]
    ;;we turn lists into vectors and back so that we can assoc-in on them
    (as-> x x
      (walk/prewalk #(cond-> % (seq? %) (-> vec (with-meta {::list true}))) x)
      (reduce (fn [x d]
                (let [{:keys [path]} (extract d)]
                  (assoc-in x path (f d))))
              x diffs)
      (walk/prewalk #(cond-> % (and (vector? %) (-> % meta ::list true?)) vec) x))))

(defn compress [[x & _ :as coll]]
  (let [diff* (partial apply diff)]
    (->> coll
         (partition 2 1)
         (map (comp diff* reverse))
         (cons x))))

(defn decompress [[x & xs]] (reductions patch x xs))
