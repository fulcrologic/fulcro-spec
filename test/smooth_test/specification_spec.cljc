(ns smooth-test.specification-spec
  #?(:clj
     (:require [smooth-test.parser.parse :refer [specification specification-fn info]]
               [smooth-test.core]
               [clojure.test :as t

                :refer (is deftest with-test run-tests testing)]))
  #?(:cljs (:require-macros [smooth-test.parser.parse :as p]
                   [cljs.test :refer (is deftest run-tests)]))
  #?(:cljs (:require
            [smooth-test.core]
            cljs.core
            cljs.pprint
            ))
  )


(defn other [] (+ 1 1))
(defn andanother [] 1)
(defn sample [] (* 5 (other)))
(defn sample2 [] (* 5 (other) (andanother)))
(defn sample3 [] (* 10 (other) (andanother)))

(def simple-spec (specification-fn '(:unit "Specification1" (p/info "title1")
                                                          (p/info "title2")
                                                          (p/info "title4"))))

(testing "Specification"
  (testing "includes test meta-data"

    (is (= (name (first simple-spec)) "with-meta"))
  ))

(specification "Specification1"
                 (info "title1")
                 (info "title2")
                 (info "title4")
                 )

;(p/specification "Specification2"
;                 (p/behavior "can run behaviors without a provider"
;                             clock-ticks => 100
;                             (sample) => 10
;                             )
;                 )
;
;(p/specification "Specification3"
;                 (p/behavior "can run behaviors without a provider"
;                             clock-ticks => 100
;                             (sample) => 10
;                             )
;                 (p/behavior "can run 2 behaviors without a provider"
;                             clock-ticks => 100
;                             (sample) => 10
;                             )
;                 )
;
;(p/specification "Specification4"
;                 (p/provided "if I force other function to return 4 and a second function to return 2"
;                             (other) => 4
;                             (andanother) => 2
;                             (p/behavior "then it better"
;                                         (sample2) => 40)
;                             )
;                 )
;
;(p/specification "Specification5"
;                 (p/provided "if I force other function to return 4 and a second function to return 2"
;                             (other) => 4
;                             (andanother) => 2
;                             (p/behavior "behavior 1 better"
;                                         (sample2) => 40)
;                             (p/behavior "behavior 2 better"
;                                         (sample3) => 80)
;                             )
;                 )
;
;; this one is failing
;(p/specification "Specification6"
;                 (p/provided "if I force other function to return 4 and a second function to return 2"
;                             (other) => 4
;                             (p/behavior "then it better"
;                                          (sample2) => 20)
;                             (p/provided "Second level provided"
;                                         (andanother) => 2
;                                         (p/behavior "then it better"
;                                                     (sample2) => 40)
;                                         (p/behavior "then it better"
;                                                     (sample3) => 80)
;
;                                         )
;                             )
;                 )
;
;
;
