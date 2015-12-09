(ns untangled-spec.stub-spec
  #?(:clj
      (:require [untangled-spec.stub :as s]
                [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report assert-expr)]
                [untangled-spec.core :refer [specification behavior provided with-timeline async tick assertions]]
                ))
  #?(:clj
      (:import clojure.lang.ExceptionInfo))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]
                            [untangled-spec.core :refer [specification behavior provided with-timeline async tick assertions]]))
  #?(:cljs (:require
             [cljs.test :as t :refer (do-report)]
             [untangled-spec.stub :as s :include-macros true]
             ))
  )

(defn make-simple-script []
  (s/make-script "something"
                 [
                  (s/make-step 'stub 1 [])
                  ]))
(specification "increment-script-call-count"
               (behavior "finds and increments the correct step"
                         (let [script (make-simple-script)]

                           (s/increment-script-call-count script 0)

                           (is (= 1 (get-in @script [:steps 0 :times])))
                           )
                         )
               )

(specification "step-complete"
               (let [script (make-simple-script)]
                 (behavior "is false when call count is less than expected count"
                           (is (not (s/step-complete script 0))))
                 (s/increment-script-call-count script 0)
                 (behavior "is true when call count reaches expected count"
                           (is (s/step-complete script 0)))
                 )
               )

(defn make-single-call-script [to-call & [literals]]
  (s/make-script "something"
                 [
                  (s/make-step to-call 1 (or literals []))
                  ]))

(defn throws! [n] (inc n))

(specification "scripted-stub"
               (behavior "calls the stub function"
                         (let [detector (atom false)
                               script (make-single-call-script (fn [] (reset! detector true)))
                               sstub (s/scripted-stub script)]

                           (s/try-or (sstub) false)

                           (is (= true @detector))
                           )
                         )

               (behavior "verifies the stub fn is called with the correct literals"
                         (let [script (make-single-call-script (fn [n x]
                                                                 [(inc n) x])
                                                               [41 :untangled-spec.provided/any])
                               sstub (s/scripted-stub script)]
                           (is (thrown? ExceptionInfo
                                        (sstub 2 :whatever)))
                           (is (= [42 :foo]
                                  (sstub 41 :foo)))))

               (behavior "returns whatever the stub function returns"
                         (let [script (make-single-call-script (fn [] 42))
                               sstub (s/scripted-stub script)]
                           (is (= 42 (sstub)))
                           ))
               (behavior "throws an exception if the function is invoked more than programmed with verify-error set to true"
                         (let [script (make-single-call-script (fn [] 42))
                               sstub (s/scripted-stub script)]

                           (sstub)                                              ; first call

                           (try (sstub) (catch ExceptionInfo e (is (= true (-> (ex-data e) :untangled-spec.stub/verify-error)))))
                           )
                         )
               (behavior "throws whatever exception the function throws"
                         (let [script (make-single-call-script (fn [] (throw (ex-info "BUMMER" {}))))
                               sstub (s/scripted-stub script)]

                           #?(:clj (is (thrown? clojure.lang.ExceptionInfo (sstub)))
                                   :cljs (is (thrown? ExceptionInfo (sstub))))
                           )
                         )
               (behavior "only moves to the next script step if the call count for the current step reaches the programmed amount"
                         (let [a-count (atom 0)
                               b-count (atom 0)
                               script (s/make-script "something" [
                                                                  (s/make-step (fn [] (swap! a-count inc)) 2 [])
                                                                  (s/make-step (fn [] (swap! b-count inc)) 1 nil)
                                                                  ])
                               sstub (s/scripted-stub script)]
                           ; first call
                           (sstub)
                           (is (= 1 @a-count))
                           ; second call
                           (sstub)
                           (is (= 2 @a-count))
                           (is (= 0 @b-count))

                           (sstub)
                           (is (= 2 @a-count))
                           (is (= 1 @b-count))
                           )
                         )
               )

(specification "validate-target-function-counts"
               (behavior "returns nil if a target function has been called enough times"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 5 :times 5}]})]]
                           (is (nil? (s/validate-target-function-counts script-atoms)))
                           )
                         )
               (behavior "throws an exception when a target function has not been called enough times"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times 5}]})]]
                           #?(:clj  (is (thrown? clojure.lang.ExceptionInfo (s/validate-target-function-counts script-atoms)))
                              :cljs (is (thrown? ExceptionInfo (s/validate-target-function-counts script-atoms))))
                           )
                         )

               (behavior "returns nil if a target function has been called enough times with :many specified"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times :many}]})]]
                           (is (nil? (s/validate-target-function-counts script-atoms)))
                           )
                         )

               (behavior "throws an exception if a function has not been called at all with :many was specified"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 0 :times :many}]})]]
                           #?(:clj  (is (thrown? clojure.lang.ExceptionInfo (s/validate-target-function-counts script-atoms)))
                              :cljs (is (thrown? ExceptionInfo (s/validate-target-function-counts script-atoms))))
                           )
                         )

               (behavior "returns nil all the function have been called the specified number of times"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                                             (atom {:function "fun2" :steps [{:ncalled 1 :times 1}]})
                                             ]]
                           (is (nil? (s/validate-target-function-counts script-atoms)))
                           )
                         )

               (behavior "throws an exception if the second function has not been called at all with :many was specified"
                         (let [script-atoms [(atom {:function "fun1" :steps [{:ncalled 1 :times 1}]})
                                             (atom {:function "fun2" :steps [{:ncalled 0 :times 1}]})
                                             ]]
                           #?(:clj  (is (thrown? clojure.lang.ExceptionInfo (s/validate-target-function-counts script-atoms)))
                              :cljs (is (thrown? ExceptionInfo (s/validate-target-function-counts script-atoms))))
                           )
                         )
               )
