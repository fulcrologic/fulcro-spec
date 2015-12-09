(ns untangled-spec.stub
  #?(:clj
     (:require [clojure.test :refer (is)]))
  #?(:cljs (:require-macros [cljs.test :refer (is)]))
  )

(defn make-step [stub times literals]
  {:stub stub :times times
   :ncalled 0 :literals literals})
(defn make-script [function steps]
  (atom {:function function
         :steps steps}))

#?(:clj
   (defmacro try-or [f dflt]
     #?(:cljs `(try ~f (catch js/Object ~'e ~dflt))
        :clj  `(try ~f (catch Exception ~'e ~dflt)))
     ))

#?(:clj
   (defmacro dp [f]
     `(let [rv# ~f]
        (println (str '~f " => " rv#))
        rv#
        )
     ))

(defn increment-script-call-count [script-atom step]
  (swap! script-atom (fn [m] (update-in m [:steps step :ncalled] inc)))
  )

(defn step-complete [script-atom step]
  (let [ncalled (get-in @script-atom [:steps step :ncalled])
        target-times (get-in @script-atom [:steps step :times])]
    (= ncalled target-times)
    ))

(defn check-matching [arg ?literal]
  (case ?literal
    :untangled-spec.provided/any true
    (= arg ?literal)))

(defn scripted-stub [script-atom]
  (let [step (atom 0)]
    (fn [& args]
      (let [target-function (:function @script-atom)
            max-calls (count (:steps @script-atom))]
        (if (< @step max-calls)
          (let [{:keys [stub literals]} (-> @script-atom :steps (nth @step))]
            (when-not (and (= (count literals)
                              (count args))
                           (every? true?
                                   (map check-matching
                                        args literals)))
              (throw (ex-info (str target-function
                                   " was called with wrong arguments")
                              {})))
            (try (apply stub args)
                 (catch #?(:clj Exception :cljs js/Object) e
                   (throw e))
                 (finally
                   (increment-script-call-count script-atom @step)
                   (if (step-complete script-atom @step) (swap! step inc))
                   ))
            )
          (throw (ex-info (str "VERIFY ERROR: " target-function " was called too many times!")
                          {::verify-error true
                           :max-calls max-calls}))
          ))
      ))
  )


(defn validate-step-counts
  "argument step contains keys:
   - :ncalled, actual number of times called
   - :times, expected number of times called"
  [errors step]
  (conj errors
        (if (or (= (:ncalled step) (:times step))
                (and (= (:times step) :many)
                     (> (:ncalled step) 0))
                )
          :ok :error
          ))
  )

(defn validate-target-function-counts [script-atoms]
  (loop [atoms script-atoms]
    (if (not-empty atoms)
      (let [function @(first atoms)
            count-results (reduce validate-step-counts [] (:steps function))
            errors? (some #(= :error %) count-results)]
        (when errors?
          (throw (ex-info (str "VERIFY ERROR: "
                               (:function function)
                               " was not called as many times as specified")
                          {::verify-error true})))
        (recur (rest atoms))))
    )
  )
