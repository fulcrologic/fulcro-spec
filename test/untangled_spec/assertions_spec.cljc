(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core #?(:clj :refer :cljs :refer-macros)
             [specification behavior provided assertions]]
            [untangled-spec.assertions
             :refer [exception-matches? triple->assertion]]
            #?(:clj [clojure.test :refer [is]]))
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
        (behavior "checks the exception is of the specified type or throws"
          (assertions
            (exception-matches? (ex-info "foo" {})
                                clojure.lang.ExceptionInfo)
            => true
            (exception-matches? (ex-info "foo" {})
                                java.lang.Error)
            =throws=> (clojure.lang.ExceptionInfo #"")))
        (behavior "checks the exception's message matches a regex or throws"
          (assertions
            (exception-matches? (ex-info "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn" {})
                                clojure.lang.ExceptionInfo #"(?i)cthulhu")
            => true
            (exception-matches? (ex-info "kthxbye" {})
                                clojure.lang.ExceptionInfo #"cthulhu")
            =throws=> (clojure.lang.ExceptionInfo #"not match regex"))))

      (behavior "triple->assertion"
        (behavior "checks equality with the => arrow"
          (assertions
            (triple->assertion '(left => right))
            =fn=> (check-assertion #(->> % first (= 'clojure.core/=)))))
        (behavior "verifies actual with the =fn=> function"
          (assertions
            (triple->assertion '(left =fn=> right))
            =fn=> (check-assertion #(->> % first (= 'right)))))
        (behavior "verifies that actual threw an exception with the =throws=> arrow"
          (assertions
            (triple->assertion '(left =throws=> (right)))
            =fn=> (check-assertion
                    #(and (->> % first (= 'try))
                          (->> % second (= 'left))
                          (-> % (nth 2) first (= 'throw))
                          (->> % last first (= 'catch))))))
        (defn index-of [sub]
          (fn [s] (.indexOf s sub)))
        (behavior "any other arrow, throws an ex-info"
          (assertions
            (triple->assertion '(left =bad-arrow=> right))
            =throws=> (ExceptionInfo
                        #"invalid arrow"
                        #(-> % ex-data (= {:arrow '=bad-arrow=>}))))))

      (behavior "assertion arrow"
        (provided "=throws=> fails if nothing threw an Exception"
          (ex-info x y) => (Exception. (str x y))
          (assertions
            [:foo :bar] =throws=> (Exception #"Expected an 'Exception'")))
        (behavior "TODO: =throws=> can catch AssertionErrors"
          (let [f (fn [x] {:pre [(even? x)]} (inc x))]
            (is (thrown? AssertionError (f 1)))
            (is (= 3 (f 2)))
            (assertions
              (f 1) =throws=> (AssertionError #"even\? x")
              (f 6) => 7
              (f 2) => 3)))
        (behavior "=> catches unexpected exceptions"
          (let [e (ex-info "asdf" {})]
            (assertions
              (throw e) => e)))
        (provided "=fn=> catches unexpected exceptions"
          (untangled-spec.assertions/handle-exception e) => 1
          (let [e (ex-info "foobar" {})]
            (assertions
              (throw e) =fn=> odd?))))))
