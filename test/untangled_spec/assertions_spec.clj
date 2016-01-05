(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core :refer
             [specification behavior provided assertions]]
            [untangled-spec.assertions
             :refer [triple->assertion exception-matches?]]
            [clojure.test :refer [is]]
            [contains.core :refer [*contains?]])
  (:import clojure.lang.ExceptionInfo))

(defn check-assertion [expected]
  (fn [actual]
    (and
      (->> actual first (= 'clojure.test/is))
      (->> actual second (= expected))
      (->> actual last first (= `str)))))

(specification "untangled-spec.assertions-spec"
  (behavior "exception-matches?"
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
        =fn=> (check-assertion '(clojure.core/= left right))))
    (behavior "verifies actual with the =fn=> function"
      (assertions
        (triple->assertion false '(left =fn=> right))
        =fn=> (check-assertion '(call right left))))
    (behavior "verifies that actual threw an exception with the =throws=> arrow"
      (assertions
        (triple->assertion false '(left =throws=> (right)))
        =fn=> (check-assertion '(throws? false left right))))
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
          (f 2) => 3)))))
