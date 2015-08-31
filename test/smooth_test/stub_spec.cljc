(ns smooth-test.stub-spec
  #?(:clj
     (:require [smooth-test.stub :as s]
               [clojure.test :as t :refer (are is deftest with-test run-tests testing)]
               ))
  #?(:clj
     (:import clojure.lang.ExceptionInfo))
  #?(:cljs (:require-macros [cljs.test :refer (are is deftest run-tests testing)]))
  #?(:cljs (:require [cljs.test :as t]
             [smooth-test.stub :as s :include-macros true]
             ))
  )

(defn make-simple-script [] (atom (s/make-script "something"
                                                 [
                                                  (s/make-step 'stub 1)
                                                  ])))
(deftest increment-script-call-count
  (testing "finds and increments the correct step"
    (let [script (make-simple-script)]

      (s/increment-script-call-count script 0)

      (is (= 1 (get-in @script [:steps 0 :times])))
      )
    )
  )

(deftest step-complete
  (let [script (make-simple-script)]
    (testing "is false when call count is less than expected count" (is (not (s/step-complete script 0))))
    (s/increment-script-call-count script 0)
    (testing "is true when call count reaches expected count" (is (s/step-complete script 0)))
    )
  )

(defn make-single-call-script [to-call] (atom (s/make-script "something"
                                                             [
                                                              (s/make-step to-call 1)
                                                              ])))

(deftest scripted-stub
  (testing "calls the stub function"
    (let [detector (atom false)
          script (make-single-call-script (fn [] (reset! detector true)))
          sstub (s/scripted-stub script)]

      (s/tryo (sstub) false)

      (is (= true @detector))
      )
    )
  (testing "returns whatever the stub function returns"
    (let [script (make-single-call-script (fn [] 42))
          sstub (s/scripted-stub script)]
      (is (= 42 (sstub)))
      ))
  (testing "throws an exception if the function is invoked more than programmed with verify-error set to true"
    (let [script (make-single-call-script (fn [] 42))
          sstub (s/scripted-stub script)]

      (sstub)                                               ; first call

      (try (sstub) (catch ExceptionInfo e (is (= true (-> (ex-data e) :smooth-test.stub/verify-error)))))
      )
    )
  (testing "throws whatever exception the function throws"
    (let [script (make-single-call-script (fn [] (throw (ex-info "BUMMER" {}))))
          sstub (s/scripted-stub script)]

      #?(:clj  (is (thrown? clojure.lang.ExceptionInfo (sstub)))
         :cljs (is (thrown? ExceptionInfo (sstub))))
      )
    )
  (testing "only moves to the next script step if the call count for the current step reaches the programmed amount"
    (let [a-count (atom 0)
          b-count (atom 0)
          script (atom (s/make-script "something" [
                                                   (s/make-step (fn [] (swap! a-count inc)) 2)
                                                   (s/make-step (fn [] (swap! b-count inc)) 1)
                                                   ]))
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
