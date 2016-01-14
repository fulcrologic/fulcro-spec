(ns untangled-spec.reporters.impl.diff
  (:require [clojure.set :as set]))

(defn dbg
  ([x] (dbg :dbg x))
  ([tag x]
   (println tag x)))

(declare diff)
(def nf '...nothing...)

(defn extract [d]
  (let [path (vec (drop-last d))
        [_ exp _ got] (last d)]
    {:path path :exp exp :got got}))

(defn diff-paths? [?d]
  (letfn [(diff? [[p _ m _]] (and (= p :+) (= m :-)))]
    (and ?d (vector? ?d) (every? vector ?d)
      (every? (comp diff? last) ?d))))

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
      (recur is es as (conj paths [i [:+ e :- nf]]))

      (and (empty? exp) (seq act))
      (recur is es as (conj paths [i [:+ nf :- a]]))

      (every? empty? [exp act]) paths

      :else (recur is es as paths))))

(defn set-diff [exp act]
  (let [missing-from-act (set/difference act exp)
        missing-from-exp (set/difference exp act)]
    (if (and (seq missing-from-act) (seq missing-from-exp))
      [:+ missing-from-exp :- missing-from-act]
      [])))

(defn diff [exp act & [opt]]
  (let [recur? (#{:recur} opt)
        wrap-in-paths #(-> % vector vector)]
    (cond
      (every? map? [exp act])
      (map-diff (vec (set (mapcat keys [exp act])))
                exp act)

      (every? string? [exp act])
      (cond-> [:+ exp :- act]
        (not recur?) wrap-in-paths)

      (every? set? [exp act])
      (cond-> (set-diff exp act)
        (not recur?) wrap-in-paths)

      (every? sequential? [exp act])
      (seq-diff exp act)

      (not= (type exp) (type act))
      (cond-> [:+ exp :- act]
        (not recur?) wrap-in-paths)

      (every? coll? [exp act])
      (seq-diff exp act)

      ;; RECUR GUARD
      (not recur?) nil

      (not= exp act)
      [:+ exp :- act]

      :else [])))
