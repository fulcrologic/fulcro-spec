(ns smooth-test.stub
  #?(:clj
     (:require [clojure.test :refer (is)]))
  #?(:cljs (:require-macros [cljs.test :refer (is)]))
  )

(defn make-step [stub times] {:stub stub :times times :ncalled 0})
(defn make-script [function steps] (atom {:function function :steps steps}))

#?(:clj
   (defmacro tryo [f dflt]
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

(defn scripted-stub [script-atom]
  (let [step (atom 0)]
    (fn [& args]
      (let [target-function (:function @script-atom)
            max-calls (count (:steps @script-atom))]
        (if (< @step max-calls)
          (let [stub (-> @script-atom :steps (nth @step) :stub)
                rv (apply stub args)                        ;; FIXME: exception handling for arg verification ONLY
                ]
            (increment-script-call-count script-atom @step)
            (if (step-complete script-atom @step) (swap! step inc))
            rv
            )
          (throw (ex-info (str "VERIFY ERROR: " target-function " was called too many times!") {::verify-error true}))
          ))
      ))
  )
