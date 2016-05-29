(ns untangled-spec.assertions-spec
  (:require [untangled-spec.core :refer
             [specification component behavior provided assertions]]
            [untangled-spec.assertions
             :as ae
             :refer [triple->assertion exception-matches?]]
            [clojure.test :as t :refer [is]]
            [untangled-spec.contains :refer [*contains?]]
            [clojure.spec :as s])
  (:import clojure.lang.ExceptionInfo))

(defn check-assertion [expected]
  (fn [actual]
    (and
      (->> actual first (= 'clojure.test/is))
      (->> actual second (= expected)))))

(defn conform-or-explain [spec x]
  (as-> (s/conform spec x) $
    (if (= :clojure.spec/invalid $)
      (throw (ex-info "Invalid data" (s/explain-data spec x)))
      $)))

(defn test-triple->assertion [form]
  (triple->assertion false (conform-or-explain ::ae/triple form)))

(defn test-block->asserts [form]
  (ae/block->asserts true (conform-or-explain ::ae/block form)))

(specification "exception-matches?"
  (behavior "checks the exception is of the specified type or throws"
    (assertions
      (exception-matches? "msg1" (ex-info "foo" {})
                          clojure.lang.ExceptionInfo)
      =fn=> (*contains? {:type :pass :message "msg1"})
      (exception-matches? "msg2" (ex-info "foo" {})
                          java.lang.Error)
      =fn=> (*contains? {:type :fail :extra "exception did not match type"
                         :actual clojure.lang.ExceptionInfo :expected java.lang.Error})))
  (behavior "checks the exception's message matches a regex or throws"
    (assertions
      (exception-matches? "msg3" (ex-info "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn" {})
                          clojure.lang.ExceptionInfo #"(?i)cthulhu")
      =fn=> (*contains? {:type :pass})
      (exception-matches? "msg4" (ex-info "kthxbye" {})
                          clojure.lang.ExceptionInfo #"cthulhu")
      =fn=> (*contains? {:type :fail :extra "exception's message did not match regex"
                         :actual "kthxbye" :expected "cthulhu"})))
  (behavior "checks the exception with the user's function"
    (let [cthulhu-bored (ex-info "Haskell 101" {:cthulhu :snores})]
      (assertions
        (exception-matches? "msg5" (ex-info "H.P. Lovecraft" {:cthulhu :rises})
                            clojure.lang.ExceptionInfo #"(?i)lovecraft"
                            #(-> % ex-data :cthulhu (= :rises)))
        =fn=> (*contains? {:type :pass})
        (exception-matches? "msg6" cthulhu-bored
                            clojure.lang.ExceptionInfo #"Haskell"
                            #(-> % ex-data :cthulhu (= :rises)))
        =fn=> (*contains? {:type :fail :actual cthulhu-bored
                           :extra "checker function failed"})))))

(specification "triple->assertion"
  (behavior "checks equality with the => arrow"
    (assertions
      (test-triple->assertion '(left => right))
      =fn=> (check-assertion '(= right left))))
  (behavior "verifies actual with the =fn=> function"
    (assertions
      (test-triple->assertion '(left =fn=> right))
      =fn=> (check-assertion '(exec right left))))
  (behavior "verifies that actual threw an exception with the =throws=> arrow"
    (assertions
      (test-triple->assertion '(left =throws=> (right)))
      =fn=> (check-assertion '(throws? false left right))))
  (behavior "any other arrow, throws an ex-info"
    (assertions
      (test-triple->assertion '(left =bad-arrow=> right))
      =throws=> (ExceptionInfo #"Invalid data"
                  #(-> % ex-data ::s/problems
                     (= {[:arrow]
                         {:pred '(comp #{"=>" "=fn=>" "=throws=>"} str)
                          :val '=bad-arrow=>
                          :via [::ae/arrow]
                          :in [1]}}))))))

(specification "throws assertion arrow"
  (provided "fails if nothing threw an Exception"
    (ex-info x y) => (Exception. (str x y))
    (assertions
      [:foo :bar] =throws=> (Exception #"Expected an 'Exception'")))
  (behavior "catches AssertionErrors"
    (let [f (fn [x] {:pre [(even? x)]} (inc x))]
      (is (thrown? AssertionError (f 1)))
      (is (= 3 (f 2)))
      (assertions
        (f 1) =throws=> (AssertionError #"even\? x")
        (f 6) => 7
        (f 2) => 3))))

(specification "running assertions reports the correct data"
  (let [get-exp-act (fn [{exp :expected act :actual msg :message extra :extra} & [opt]]
                      (case opt
                        :all [act exp msg extra]
                        :msg [act exp msg]
                        :ae  [act exp extra]
                        [act exp]))
        test-case (fn [x & [opt]]
                    (binding [t/report (fn [m] m)]
                      (-> x test-triple->assertion eval (get-exp-act opt))))]
    (component "=>"
      (behavior "literals"
        (is (= [5 3] (test-case '(5 => 3)))))
      (behavior "forms"
        (is (= [5 3 "(+ 3 2) => (+ 2 1)"]
               (test-case '((+ 3 2) => (+ 2 1)) :msg))))
      (behavior "unexpected throw"
        (is (= ["clojure.lang.ExceptionInfo: bad {}"
                "(= \"good\" (throw (ex-info \"bad\" {})))"
                "(throw (ex-info \"bad\" {})) => good"]
               (mapv str (test-case '((throw (ex-info "bad" {})) => "good") :msg))))))

    (component "=fn=>"
      (behavior "literals"
        (is (= [5 'even? "5 =fn=> even?"]
               (test-case '(5 =fn=> even?) :msg))))
      (behavior "lambda"
        (is (re-find #"even\?"
                     (->> '(7 =fn=> #(even? %))
                       test-case second str))))
      (behavior "forms"
        (is (= [7 '(fn [x] (even? x))]
               (test-case '((+ 5 2) =fn=> (fn [x] (even? x))))))))

    (component "=throws=>"
      (behavior "reports if the message didnt match the regex"
        (is (= ["foo", "asdf", "(throw (ex-info \"foo\" {})) =throws=> (clojure.lang.ExceptionInfo #\"asdf\")"
                "exception's message did not match regex"]
               (test-case '((throw (ex-info "foo" {}))
                            =throws=> (clojure.lang.ExceptionInfo #"asdf"))
                          :all))))
      (behavior "reports if nothing was thrown"
        (is (= ["it to throw", "(+ 5 2) =throws=> (clojure.lang.ExceptionInfo #\"asdf\")"
                "Expected an 'clojure.lang.ExceptionInfo' to be thrown!"]
               (-> '((+ 5 2) =throws=> (clojure.lang.ExceptionInfo #"asdf"))
                 (test-case :all) rest))))))

  (component "triple?"
    (behavior "=> must be a symbol"
      (is (not (ae/triple? [:left "=>" :exp]))))
    (behavior "arrow arg starts with = and ends with >"
      (is (ae/triple? [:left '=> :exp]))
      (is (ae/triple? [:left '=fn=> :exp]))))

  (component "forms->blocks"
    (behavior "groups forms into assertion blocks"
      (is (=
           '(("string1" (a => b) (c => d))
             ("string2" (d => e)))
           (ae/forms->blocks '("string1"
                                a => b
                                c => d
                                "string2"
                                d => e)))))
    (behavior "no behavior strings"
      (is (=
           '(((a => b) (c => d) (d => e)))
           (ae/forms->blocks '(a => b
                                 c => d
                                 d => e)))))
    (behavior "leading block lacks string but others have string"
      (is (=
           '(((a => b) (c => d))
             ("string2" (d => e)))
           (ae/forms->blocks '(a => b
                                 c => d
                                 "string2"
                                 d => e)))))
    (behavior "fails when there are no blocks after string"
      (is (thrown-with-msg?
            AssertionError
            #"behavior string without trailing assertions"
            (ae/forms->blocks '("string1")))))

    (behavior "fails when there are two consecutive strings"
      (is (thrown-with-msg?
            AssertionError
            #"behavior string without trailing assertions"
            (ae/forms->blocks '("string1" "string2" a => b)))))
    (behavior "foo"
      (is (thrown-with-msg?
            AssertionError
            #"malformed arrow"
            (ae/forms->blocks '(a => b c =>))))))

  (component "block->asserts"
    (behavior "wraps triples in behavior do-reports"
      (let [asserts (rest (test-block->asserts '("string2" d => e)))]
        (is (= '(cljs.test/do-report {:type :begin-behavior :string "string2"})
               (first asserts)))
        (is (= '(cljs.test/do-report {:type :end-behavior :string "string2"})
               (last asserts)))))
    (behavior "does not wrap in do-report if there is no string"
      (let [asserts (rest (test-block->asserts '(d => e)))]
        (is (not= 'cljs.test/do-report
                  (ffirst asserts)))))
    (behavior "converts triples to assertions"
      (let [asserts (drop-last (drop 2 (test-block->asserts '("string2" d => e))))]
        (is (every?
              #{'cljs.test/is}
              (map first asserts))))
      (let [asserts (drop-last (drop 2 (test-block->asserts '("string2" d => e))))]
        (is (every?
              #{'cljs.test/is}
              (map first asserts)))))))
