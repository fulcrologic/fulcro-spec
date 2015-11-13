(ns untangled-spec.core-spec
  #?(:clj
      (:require [untangled-spec.core :as c :refer [specification behavior provided assertions]]
                [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report)]
                ))
  )

(defn- spy
  ([x] (spy "" x))
  ([tag x] (println (str "[" tag "]:") x) x))

#?(:clj
    (specification "untangled-spec.core-spec"
                   (behavior "assertions"
                             (behavior "works with r-side being a literal (ie not fn)"
                                       (let [f #(inc %)
                                             ast (macroexpand
                                                   `(assertions
                                                      (f 5) => :foo))
                                             let-block (second ast)
                                             is-block (nth let-block 2)
                                             eq-check (second is-block)]
                                         (assertions
                                           (first eq-check) => `=
                                           (last eq-check) => :foo))
                                       )
                             (behavior "works with r-side functions!"
                                       (let [f #(inc %)
                                             ast (macroexpand
                                                   `(assertions
                                                      (f 5) => even?))
                                             let-block (second ast)
                                             is-block (nth let-block 2)
                                             is-check (second is-block)]
                                         (assertions
                                           (first is-check) => `true?
                                           (-> is-check second first) => `even?
                                           ))
                                       )
                             )
                   )
    )
