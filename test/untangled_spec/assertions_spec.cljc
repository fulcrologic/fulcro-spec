(ns untangled-spec.assertions-spec
  #?(:clj
      (:require [untangled-spec.core :as c :refer [specification behavior provided assertions]]
                [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report)]
                [untangled-spec.assertions
                 :refer [exception-matches? triple->assertion]]
                ))
  #?(:clj
      (:import clojure.lang.ExceptionInfo))
  )

(defn check-assertion [expected]
  (fn [actual]
    (->> actual
         (take 2)
         (= expected))))

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
        (behavior "takes a 3-tuple of (actual,arrow,expected)"
          (behavior "and for the => arrow, returns an equality check"
            (assertions
              (triple->assertion '(left => right))
              =fn=> #(->> % (take 2) (= '(is (clojure.core/= left right))))
              ))
          (behavior "and for the =fn=> arrow, returns a fn call"
            (assertions
              (triple->assertion '(left =fn=> right))
              =fn=> (check-assertion '(is (right left)))
              ))
          (behavior "and for the =throws=> arrow, returns a try-catch"
            (assertions
              (triple->assertion '(left =throws=> (right)))
              =fn=> (check-assertion
                      '(is (try left
                                (catch java.lang.Exception e
                                  (untangled-spec.assertions/exception-matches?
                                    e right)))))))
          (behavior "any other arrow, throws an ex-info"
            (assertions
              (triple->assertion '(left =bad-arrow=> right))
              =throws=> (ExceptionInfo
                          #"invalid arrow"
                          #(-> % ex-data (= {:arrow '=bad-arrow=>})))))
          ))))
