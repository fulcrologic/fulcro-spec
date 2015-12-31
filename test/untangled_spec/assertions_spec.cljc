(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided assertions]]
            [untangled-spec.assertions
             :refer [triple->assertion]]
            [untangled-spec.assert-expr
             :refer [exception-matches?]]
            #?(:clj [clojure.test :refer [is]])
            [contains.core :refer [*contains?]])
  #?(:clj
      (:import clojure.lang.ExceptionInfo)))

(defn check-assertion [expected]
  (fn [actual]
    (let [is-block (if (= "untangled-is" (name (first actual)))
                     actual (->> actual (drop 2) first))]
      (and
        ;verify is using `is
        (->> is-block first
             (= 'untangled-spec.assertions/untangled-is))
        ;call expected with $this eg: (is $this ...)
        (->> is-block second
             (expected))))))

#?(:clj
    (specification "untangled-spec.assertions-spec"
      (behavior "exception-matches?"
        ;TODO: use anthony's *contains library
        (behavior "checks the exception is of the specified type or throws"
          (assertions
            (exception-matches? "msg1" (ex-info "foo" {})
                                clojure.lang.ExceptionInfo)
            =fn=> (*contains? {:type :passed :message "msg1"})
            (exception-matches? "msg2" (ex-info "foo" {})
                                java.lang.Error)
            => {:type :fail :message "exception did not match type"
                :actual clojure.lang.ExceptionInfo :expected java.lang.Error}))
        (behavior "checks the exception's message matches a regex or throws"
          (assertions
            (exception-matches? "msg3" (ex-info "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn" {})
                                clojure.lang.ExceptionInfo #"(?i)cthulhu")
            =fn=> (*contains? {:type :passed :message "msg3"})
            (exception-matches? "msg4" (ex-info "kthxbye" {})
                                clojure.lang.ExceptionInfo #"cthulhu")
            => {:type :fail :message "exception's message did not match regex"
                :actual "kthxbye" :expected "cthulhu"})))

      (behavior "triple->assertion"
        (behavior "checks equality with the => arrow"
          (assertions
            (triple->assertion false '(left => right))
            =fn=> #(and (-> % first name (= "is"))
                        (-> % second second (= 'left))
                        (-> % second last (= 'right)))))
        (behavior "verifies actual with the =fn=> function"
          (assertions
            (triple->assertion false '(left =fn=> right))
            =fn=> #(and (-> % first name (= "is"))
                        (-> % second first (= 'call))
                        (-> % second second (= 'right))
                        (-> % second last (= 'left)))))
        (behavior "verifies that actual threw an exception with the =throws=> arrow"
          (assertions
            (triple->assertion false '(left =throws=> (right)))
            =fn=> #(and (->> % first name (= "is"))
                        (->> % second first (= 'throws?)))))
        (defn index-of [sub]
          (fn [s] (.indexOf s sub)))
        (behavior "any other arrow, throws an ex-info"
          (assertions
            (triple->assertion false '(left =bad-arrow=> right))
            =throws=> (ExceptionInfo
                        #"invalid arrow"
                        #(-> % ex-data (= {:arrow '=bad-arrow=>}))))))

      (behavior "assertion arrow"
        (provided "=throws=> fails if nothing threw an Exception"
          (ex-info x y) => (Exception. (str x y))
          (assertions
            [:foo :bar] =throws=> (Exception #"Expected an 'Exception'")))
        (behavior "=throws=> can catch AssertionErrors"
          (let [f (fn [x] {:pre [(even? x)]} (inc x))]
            (is (thrown? AssertionError (f 1)))
            (is (= 3 (f 2)))
            (assertions
              (f 1) =throws=> (AssertionError #"even\? x")
              (f 6) => 7
              (f 2) => 3))))))
