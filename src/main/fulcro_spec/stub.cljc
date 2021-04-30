(ns fulcro-spec.stub
  (:require
    [clojure.test :as t]))

(defn make-step [stub-function ntimes literals mock-arglist]
  {:stub         stub-function
   :times        ntimes
   :ncalled      0
   :literals     literals
   :history      []
   :mock-arglist mock-arglist})

(defn make-script [function steps]
  (atom {:function function :steps steps :history [] :returned []}))

(defn increment-script-call-count [script-atom step]
  (swap! script-atom update-in [:steps step :ncalled] inc))

(defn step-complete [script-atom step]
  (let [{:keys [ncalled times]}
        (get-in @script-atom [:steps step])]
    (= ncalled times)))

(defn zip-pad [pad & colls]
  (let [ncolls (count colls)]
    (->> colls
      (map #(concat % ((if (fn? pad) repeatedly repeat) pad)))
      (apply interleave)
      (take (* ncolls (apply max (map count colls))))
      (partition ncolls))))

(defn valid-args? [literals args]
  (or (not literals)
    (let [reduced-if (fn [p x] (cond-> x (p x) reduced))]
      (reduce (fn [_ [lit arg]]
                (reduced-if false?
                  (case lit
                    ::&_ (reduced true)
                    ::any true
                    (= lit arg))))
        true (zip-pad gensym literals args)))))

(def ^:dynamic *real-return-fn* (constantly nil))

(defn scripted-stub [script-atom]
  (let [step (atom 0)]
    (fn [& args]
      (let [{:keys [function steps ncalled]} @script-atom
            max-calls (count steps)
            curr-step @step]
        (if (>= curr-step max-calls)
          (throw (ex-info (str function " was called too many times!")
                   {:max-calls max-calls
                    :args      args}))
          (let [{:keys [stub literals]} (nth steps curr-step)]
            (when-not (valid-args? literals args)
              (throw (ex-info (str function " was called with wrong arguments")
                       {:args args :expected-literals literals})))
            (swap! script-atom
              #(-> % (update :history conj args)
                 (update-in [:steps curr-step :history] conj args)))
            (try
              (let [return (binding [*real-return-fn* (fn [] (apply function args))]
                             (apply stub args))]
               (swap! script-atom update :returned conj return)
               return)
              ;; NOTE: In cljs this is not really meant to catch anything
              #?(:clj
                 (catch clojure.lang.ArityException ae
                   (throw (ex-info (str "The MOCKED version of " function " was called with " (.-actual ae)
                                     " argument(s), but the mock did not support that number of arguments.")
                            {:function function
                             :args     args}))))
              (catch #?(:clj Exception :cljs :default) e
                (throw (or (some-> e ex-data ::exception) e)))
              (finally
                (increment-script-call-count script-atom curr-step)
                (when (step-complete script-atom curr-step)
                  (swap! step inc))))))))))

(defn validate-step-counts
  "argument step contains keys:
   - :ncalled, actual number of times called
   - :times, expected number of times called"
  [errors {:as step :keys [ncalled times]}]
  (conj errors
    (if (or (= ncalled times)
          (and (= times :many)
            (> ncalled 0)))
      :ok [ncalled times])))

(defn validate-target-function-counts [script-atoms]
  (mapv (fn [script]
          (let [{:keys [function steps history]} @script
                count-results (reduce validate-step-counts [] steps)
                first-error   (first (filter #(not= :ok %) count-results))]
            (when first-error
              (t/do-report
                {:type     :error
                 :mock?    true
                 :message  (str function " was not called as many times as specified.")
                 :actual   (first first-error)
                 :expected (second first-error)}))))
    script-atoms))

(defn real-return []
  (*real-return-fn*))

(def ^:dynamic *script-by-fn* {})

(defn returns-of [f]
  (-> @(get *script-by-fn* f)
    :returned))

(defn return-of [f index]
  (-> (returns-of f)
    (nth index nil)))

(defn zip-arglist [mock-arglist args]
  (loop [ret     {}
         arglist mock-arglist
         args    args]
    (if (or (empty? arglist) (empty? args))
      ret
      (let [arglist-item (first arglist)]
        (case arglist-item
          (::literal ::ignored)
          (recur ret (rest arglist) (rest args))
          ::&_ (cond-> ret
                 (not (#{::literal ::ignored} (second arglist)))
                 (assoc (symbol (second arglist)) args))
          (recur
            (assoc ret
              (symbol arglist-item)
              (first args))
            (rest arglist)
            (rest args)))))))

(defn calls-of [f]
  (let [steps (:steps @(get *script-by-fn* f))]
    (mapcat
      (fn [{:keys [history mock-arglist]}]
        (map #(zip-arglist mock-arglist %)
          history))
      steps)))

(defn call-of [f index]
  (nth (calls-of f) index nil))

(defn spied-value [f index sym]
  (get (call-of f index) sym))
