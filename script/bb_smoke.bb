#!/usr/bin/env bb
;; Smoke test for fulcro-spec under babashka.
;; Run from project root with: bb script/bb_smoke.bb
;;
;; Exercises the bb-supported surface of the library:
;;   - specification / behavior / component
;;   - assertions with => =fn=> =throws=> =check=>
;;   - provided / when-mocking
;;   - provided! / when-mocking! (spec/malli validation path)
;;   - provided!! / when-mocking!! (transitive-coverage enforcement)

(require '[fulcro-spec.core :refer [specification behavior component assertions
                                    provided when-mocking
                                    provided! when-mocking!
                                    when-mocking!!
                                    => =fn=> =throws=> =check=>]])
(require '[fulcro-spec.check :as c])
(require '[fulcro-spec.proof :as proof])
(require '[clojure.spec.alpha :as s])
(require '[clojure.test :as t])

(defn add [a b] (+ a b))
(s/fdef add :args (s/cat :a number? :b number?) :ret number?)

(defn dbl [x] (add x x))

(specification "basic macros"
  (behavior "equality"
    (assertions
      (add 1 2) => 3
      (dbl 5)   => 10))
  (component "predicate, throws, checker"
    (assertions
      42 =fn=> pos?
      (throw (ex-info "boom" {})) =throws=> clojure.lang.ExceptionInfo
      42 =check=> (c/is?* pos?))))

(specification "mocking"
  (behavior "when-mocking rewires"
    (when-mocking
      (add a b) => 100
      (assertions (dbl 5) => 100)))
  (behavior "provided with arrow count"
    (provided "exactly two calls"
      (add a b) =2x=> 7
      (assertions (+ (add 1 2) (add 3 4)) => 14)))
  (behavior "multiple distinct mocks"
    (when-mocking
      (add a b) => 10
      (dbl x)   => 50
      (assertions
        (add 1 2) => 10
        (dbl 7)   => 50)))
  (behavior "sequential arrow counts on same fn"
    (when-mocking
      (add a b) =1x=> 99
      (add a b) =1x=> 11
      (assertions
        (add 0 0) => 99
        (add 0 0) => 11))))

(specification "bang variants validate against spec"
  (behavior "valid args ok"
    (when-mocking!
      (add a b) => 100
      (assertions (dbl 5) => 100)))
  (behavior "provided! supports arrow count"
    (provided! "exactly two"
      (add a b) =2x=> 7
      (assertions (+ (add 1 2) (add 3 4)) => 14))))

(proof/configure! {:scope-ns-prefixes #{} :enforce? false})

(specification "double-bang behaves like single-bang when not enforcing"
  (when-mocking!!
    (add a b) => 42
    (assertions (dbl 1) => 42)))

(let [{:keys [pass fail error]} (t/run-tests *ns*)]
  (println "bb smoke -> pass:" pass "fail:" fail "error:" error)
  (System/exit (if (and (zero? fail) (zero? error)) 0 1)))
