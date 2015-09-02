(ns smooth-test.specification
  #?(:clj
     (:require [clojure.test :refer [do-report test-var *load-tests*]]
               [clojure.string :as s]
               )
     )
  )

(defmacro specification
  "Defines a specificaiton which is translated into a what a deftest macro produces with report hooks for the
  description.
  When *load-tests* is false, specificaiton is ignored."
  [description & body]
  (when *load-tests*
    (let [var-name-from-string (fn [s] (symbol (s/lower-case (s/replace s #"[ ]" "-"))))
          name (var-name-from-string description)]
      `(def ~(vary-meta name assoc :test `(fn []
                                            (do-report {:type :begin-specification :string ~description})
                                            ~@body
                                            (do-report {:type :end-specification :string ~description})
                                            ))
         (fn []
           (test-var (var ~name)))))))