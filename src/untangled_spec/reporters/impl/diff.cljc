(ns untangled-spec.reporters.impl.diff)

(defn dbg
  ([x] (dbg :dbg x))
  ([tag x]
   (println tag x)))

(declare diff)
(def nf '...nothing...)

(defn extract [[_ exp _ got]]
  {:exp exp :got got})

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

(defn diff [exp act & [opt]]
  (let [recur? (#{:recur} opt)]
    (cond
      (not= (type exp) (type act))
      (cond-> [:+ exp :- act]
        (not recur?) ((comp vector vector)))

      (every? map? [exp act])
      (map-diff (vec (set (mapcat keys [exp act])))
                exp act)

      (every? coll? [exp act])
      (seq-diff exp act)

      (every? string? [exp act])
      (cond-> [:+ exp :- act]
        (not recur?) ((comp vector vector)))

      ;; RECUR GUARD
      (not recur?) nil

      (not= exp act)
      [:+ exp :- act]

      :else [])))
