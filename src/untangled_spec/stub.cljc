(ns untangled-spec.stub
  #?(:clj
     (:require [clojure.test :refer (is)]))
  #?(:cljs (:require-macros [cljs.test :refer (is)])))

(defn make-step [stub times literals]
  {:stub stub :times times
   :ncalled 0 :literals literals
   :history []})
(defn make-script [function steps]
  (atom {:function function
         :steps steps}))

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
  (let [reduced-if (fn [p x] (cond-> x p reduced))]
    (reduce (fn [_ [lit arg]]
              (reduced-if false?
                (case lit
                  ::&_ (reduced true)
                  ::any true
                  (= lit arg))))
      true (zip-pad gensym literals args))))

(defn scripted-stub [script-atom]
  (let [step (atom 0)]
    (fn [& args]
      (let [{:keys [function steps ncalled]} @script-atom
            max-calls (count steps)
            curr-step @step]
        (if (>= curr-step max-calls)
          (throw (ex-info (str function " was called too many times!")
                   {::verify-error true
                    :max-calls max-calls
                    :args args}))
          (let [{:keys [stub literals]} (nth steps curr-step)]
            (when-not (valid-args? literals args)
              (throw (ex-info (str function " was called with wrong arguments")
                       {:args args :expected-literals literals})))
            (swap! script-atom update :history conj args)
            (try (apply stub args)
              (catch #?(:clj Exception :cljs js/Object) e (throw e))
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
          :ok :error)))

(defn validate-target-function-counts [script-atoms]
  (mapv (fn [step]
          (let [{:keys [function steps history]} @step
                count-results (reduce validate-step-counts [] steps)
                errors? (some #(= :error %) count-results)]
            (when errors?
              (throw (ex-info (str function " was not called as many times as specified")
                       {::verify-error true
                        :history history})))))
    script-atoms))
