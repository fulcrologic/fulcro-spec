(ns untangled-spec.assertions-spec
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t :refer [is]]
    [untangled-spec.assertions :as ae
     :refer [check-error check-error* parse-criteria]]
    [untangled-spec.contains :refer [*contains?]]
    [untangled-spec.core
     :refer [specification component behavior assertions]]
    [untangled-spec.impl.macros :as im]
    [untangled-spec.spec :as us]
    [untangled-spec.testing-helpers :as th])
  (:import clojure.lang.ExceptionInfo))

(defn check-assertion [expected]
  (fn [actual]
    (and
      (->> actual first (= 'clojure.test/is))
      (->> actual second (= expected)))))

(defn test-triple->assertion [form]
  (ae/triple->assertion false (us/conform! ::ae/triple form)))

(defn test-block->asserts [form]
  (ae/block->asserts false (us/conform! ::ae/block form)))

(def test-regex #"a-simple-test-regex")

(specification "check-error"
  (behavior "supports many syntaxes"
    (assertions
      (us/conform! ::ae/criteria 'ExceptionInfo)
      => [:sym 'ExceptionInfo]
      (us/conform! ::ae/criteria {:ex-type 'ExceptionInfo
                                  :fn even?, :regex test-regex})
      => [:map {:ex-type 'ExceptionInfo :fn even? :regex test-regex}]
      (us/conform! ::ae/criteria ['ExceptionInfo])
      => [:list {:ex-type 'ExceptionInfo}]

      (parse-criteria [:sym 'irr]) => {:ex-type 'irr}
      (parse-criteria [:w/e 'dont-care]) => 'dont-care

      (check-error "spec-msg1" (ex-info "foo" {})
        {:ex-type ExceptionInfo})
      =fn=> (*contains? {:type :pass})
      (let [check #(-> % ex-data :ok)]
        (check-error "spec-msg2" (ex-info "foo" {:ok false})
          {:fn check} '#(some fn)))
      =fn=> (*contains? {:type :fail :expected '#(some fn)})
      (check-error "spec-msg3" (ex-info "foo" {})
        {:regex #"oo"})
      =fn=> (*contains? {:type :pass})))
  (behavior "checks the exception is of the specified type or throws"
    (assertions
      (check-error* "msg1" (ex-info "foo" {})
        clojure.lang.ExceptionInfo)
      =fn=> (*contains? {:type :pass :message "msg1"})
      (check-error* "msg2" (ex-info "foo" {})
        java.lang.Error)
      =fn=> (*contains? {:type :fail :extra "exception did not match type"
                         :actual clojure.lang.ExceptionInfo :expected java.lang.Error})))
  (behavior "checks the exception's message matches a regex or throws"
    (assertions
      (check-error* "msg3" (ex-info "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn" {})
        clojure.lang.ExceptionInfo #"(?i)cthulhu")
      =fn=> (*contains? {:type :pass})
      (check-error* "msg4" (ex-info "kthxbye" {})
        clojure.lang.ExceptionInfo #"cthulhu")
      =fn=> (*contains? {:type :fail :extra "exception's message did not match regex"
                         :actual "kthxbye" :expected "cthulhu"})))
  (behavior "checks the exception with the user's function"
    (let [cthulhu-bored (ex-info "Haskell 101" {:cthulhu :snores})]
      (assertions
        (check-error* "msg5" (ex-info "H.P. Lovecraft" {:cthulhu :rises})
          clojure.lang.ExceptionInfo #"(?i)lovecraft"
          #(-> % ex-data :cthulhu (= :rises)))
        =fn=> (*contains? {:type :pass})
        (check-error* "msg6" cthulhu-bored
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
      (test-triple->assertion '(left =throws=> right))
      =fn=> (check-assertion '(throws? false left right))))
  (behavior "any other arrow, throws an ex-info"
    (assertions
      (test-triple->assertion '(left =bad-arrow=> right))
      =throws=> (ExceptionInfo #"fails spec.*arrow"))))

(specification "throws assertion arrow"
  (behavior "catches AssertionErrors"
    (let [f (fn [x] {:pre [(even? x)]} (inc x))]
      (is (thrown? AssertionError (f 1)))
      (is (= 3 (f 2)))
      (assertions
        (f 1) =throws=> (AssertionError #"even\? x")
        (f 6) => 7
        (f 2) => 3))))

(defn get-exp-act [{exp :expected act :actual msg :message extra :extra} & [opt]]
  (case opt
    :all [act exp msg extra]
    :msg [act exp msg]
    :ae  [act exp extra]
    [act exp]))

(defmacro test-case [x & [opt]]
  `(binding [t/report identity]
     (get-exp-act ~(-> x test-triple->assertion) ~opt)))

(specification "running assertions reports the correct data"
  (component "=>"
    (behavior "literals"
      (is (= [5 3] (test-case (5 => 3)))))
    (behavior "forms"
      (is (= [5 3 "(+ 3 2) => (+ 2 1)"]
             (test-case ((+ 3 2) => (+ 2 1)) :msg))))
    (behavior "unexpected throw"
      (is (= ["clojure.lang.ExceptionInfo: bad {}"
              "(= \"good\" (throw (ex-info \"bad\" {})))"
              "(throw (ex-info \"bad\" {})) => good"]
             (mapv str (test-case ((throw (ex-info "bad" {})) => "good") :msg))))))

  (component "=fn=>"
    (behavior "literals"
      (is (= [5 'even? "5 =fn=> even?"]
             (test-case (5 =fn=> even?) :msg))))
    (behavior "lambda"
      (is (re-find #"even\?"
                   (str (second (test-case (7 =fn=> #(even? %))))))))
    (behavior "forms"
      (is (= [7 '(fn [x] (even? x))]
             (test-case ((+ 5 2) =fn=> (fn [x] (even? x)))))))
    (behavior "unexpected error"
      (is (= ["clojure.lang.ExceptionInfo: bad {}"
              "(exec even? (throw (ex-info \"bad\" {})))"
              "(throw (ex-info \"bad\" {})) =fn=> even?"]
             (mapv str (test-case ((throw (ex-info "bad" {})) =fn=> even?) :msg))))))

  (component "=throws=>"
    (behavior "reports if the message didnt match the regex"
      (is (= ["foo", "asdf", "(throw (ex-info \"foo\" {})) =throws=> (clojure.lang.ExceptionInfo #\"asdf\")"
              "exception's message did not match regex"]
             (test-case ((throw (ex-info "foo" {}))
                          =throws=> (clojure.lang.ExceptionInfo #"asdf"))
               :all))))
    (behavior "reports if nothing was thrown"
      (is (= ["it to throw", "(+ 5 2) =throws=> (clojure.lang.ExceptionInfo #\"asdf\")"
              "Expected an error to be thrown!"]
             (-> ((+ 5 2) =throws=> (clojure.lang.ExceptionInfo #"asdf"))
               (test-case :all) rest)))))

  (component "block->asserts"
    (behavior "wraps triples in behavior do-reports"
      (let [reporting (th/locate `im/with-reporting (test-block->asserts '("string2" d => e)))]
        (is (= `(im/with-reporting {:type :behavior :string "string2"})
               (take 2 reporting)))))
    (behavior "converts triples to assertions"
      (let [asserts (test-block->asserts '("string2" d => e))]
        (is (every? #{`t/is} (map first (drop 2 asserts))))))))

(specification "fix-conform for issue #31"
  (assertions
    (mapv (juxt :behavior (comp count :triples))
      (ae/fix-conform
        (us/conform! ::ae/assertions
          '("foo" 1 => 2 "bar" 3 => 4, 5 => 6 "qux" 7 => 8, 9 => 10))))
    => '[["foo" 1] ["bar" 2] ["qux" 2]]))
