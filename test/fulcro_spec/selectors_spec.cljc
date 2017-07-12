(ns fulcro-spec.selectors-spec
  (:require
    [fulcro-spec.core :refer [specification behavior component assertions]]
    [fulcro-spec.selectors :as sel]))

(defn longform [active & [inactive]]
  (vec
    (concat
      (map #(hash-map :selector/id % :selector/active? true) active)
      (map #(hash-map :selector/id % :selector/active? false) inactive))))

(specification "selectors" :focused
  (component "set-selectors"
    (assertions
      (sel/set-selectors* (longform #{:a :b :focused})
        #{:focused})
      =>  (longform #{:focused} #{:a :b})))

  (component "selected-for?"
    (assertions
      "active selectors only apply on tests that have the selector"
      (sel/selected-for?* (longform #{:focused}) #{}) => false
      (sel/selected-for?* (longform #{:focused}) nil) => false
      (sel/selected-for?* (longform #{:focused}) #{:focused}) => true

      "selected if it's an active selector or not defined"
      (sel/selected-for?* (longform #{}) #{:focused}) => true
      (sel/selected-for?* (longform #{} #{:focused}) #{:focused}) => false
      (sel/selected-for?* (longform #{:focused}) #{:focused}) => true
      (sel/selected-for?* (longform #{:focused}) #{:asdf}) => true
      (sel/selected-for?* (longform #{:focused} #{:asdf}) #{:asdf}) => false

      "must pass at least one active selector"
      (sel/selected-for?* (longform #{:unit :focused}) #{:focused}) => true
      (sel/selected-for?* (longform #{:unit :focused}) #{:qa}) => true
      (sel/selected-for?* (longform #{:unit :focused} #{:qa}) #{:qa}) => false)))
