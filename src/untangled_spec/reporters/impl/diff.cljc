(ns untangled-spec.reporters.impl.diff
  (:require [clojure.set :as set]
            [clojure.walk :as walk]))

(defn dbg
  ([x] (dbg :dbg x))
  ([tag x]
   (println tag x)))

(declare diff)
(def nf '...nothing...)

(defn extract [[path [_ exp _ got]]]
  {:path path :exp exp :got got})

(defn diff-elem
  ([] [])
  ([exp got]
   [:+ exp :- got]))

(defn diff? [?d]
  (letfn [(diff-elem? [[p _ m _]] (and (= p :+) (= m :-)))]
    (and ?d (map? ?d)
      (every? vector (keys ?d))
      (every? diff-elem? (vals ?d)))))

(defn- map-diff [ks exp act]
  (loop [[k & ks] ks, exp exp, act act, path [], paths []]
    (if-not k
      paths
      (let [ev (get exp k nf)
            av (get act k nf)]
        (if (and ev av (= ev av))
          (recur ks exp act path paths)
          (let [d (diff ev av :recur)
                d (if (= :+ (first d)) [[d]] d)
                path' (conj path k)
                path (mapv #(vec (concat path' %)) d)
                paths (vec (concat paths path))]
            (recur ks exp act [] paths)))))))

(defn- seq-diff [exp act]
  (loop [[i & is] (range), [e & es :as exp] exp, [a & as :as act] act, paths []]
    (cond
      (and (seq exp) (seq act) (not= e a))
      (let [d (diff e a :recur)
            d (if (= :+ (first d)) [[d]] d)
            paths (vec (concat paths (mapv #(vec (cons i %)) d)))]
        (recur is es as paths))

      (and (seq exp) (empty? act))
      (recur is es as (conj paths [i (diff-elem e nf)]))

      (and (empty? exp) (seq act))
      (recur is es as (conj paths [i (diff-elem nf a)]))

      (every? empty? [exp act]) paths

      :else (recur is es as paths))))

(defn set-diff [exp act]
  (let [missing-from-act (set/difference act exp)
        missing-from-exp (set/difference exp act)]
    (if (or (seq missing-from-act) (seq missing-from-exp))
      (diff-elem missing-from-exp missing-from-act)
      (diff-elem))))

(defn diff [exp act & [opt]]
  (let [recur? (#{:recur} opt)
        wrap-in-paths #(-> % vector vector)]
    (cond->
      (cond
        (every? map? [exp act])
        (map-diff (vec (set (mapcat keys [exp act])))
                  exp act)

        (every? string? [exp act])
        (cond-> (diff-elem exp act)
          (not recur?) wrap-in-paths)

        (every? set? [exp act])
        (cond-> (set-diff exp act)
          (not recur?) wrap-in-paths)

        (every? sequential? [exp act])
        (seq-diff exp act)

        (not= (type exp) (type act))
        (cond-> (diff-elem exp act)
          (not recur?) wrap-in-paths)

        (every? coll? [exp act])
        (seq-diff exp act)

        ;; RECUR GUARD
        (not recur?) nil

        (not= exp act)
        (diff-elem exp act)

        :else [])
      (not recur?) (->> (mapv #(vector (vec (drop-last %)) (last %)))
                        (into {})))))

(defn patch [x diffs & [f]]
  (let [f (or f identity)]
    ;;we turn lists into vectors and back so that we can assoc-in on them
    (as-> x x
      (walk/prewalk #(cond-> % (seq? %) (-> vec (conj ::list))) x)
      (reduce (fn [x d]
                (let [{:keys [path exp]} (extract d)]
                  (assoc-in x path (f exp))))
              x diffs)
      (walk/prewalk #(cond-> % (and (vector? %) (= ::list (last %))) drop-last) x))))
