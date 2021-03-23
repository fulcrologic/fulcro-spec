(ns fulcro-spec.stub
  #?(:clj
     (:require [clojure.test :refer (is)]))
  #?(:cljs (:require-macros [cljs.test :refer (is)])))

(defn make-step [stub times literals]
  {:stub    stub :times times
   :ncalled 0 :literals literals
   :history []})
(defn make-script [function steps]
  (atom {:function function :steps steps :history []}))

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
    (let [reduced-if (fn [p x] (cond-> x p reduced))]
      (reduce (fn [_ [lit arg]]
                (reduced-if false?
                  (case lit
                    ::&_ (reduced true)
                    ::any true
                    (= lit arg))))
        true (zip-pad gensym literals args)))))

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
            (try (apply stub args)
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
              (throw (ex-info (str function " was not called as many times as specified.\n"
                                "Expected " (second first-error) ", actual " (first first-error))
                       (merge {:mock? true} @script))))))
    script-atoms))
