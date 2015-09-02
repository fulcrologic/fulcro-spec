(ns smooth-test.behavior
  #?(:clj (:require [clojure.test :refer [do-report *testing-contexts*]])
     )
  )

(defmacro behavior
  "Adds a new string to the list of testing contexts.  May be nested,
  but must occur inside a test function (deftest)."
  {:added "1.1"}
  [string & body]
  `(binding [*testing-contexts* (conj *testing-contexts* ~string)]
     (do-report {:type :begin-behavior  :string ~string } )
     ~@body
    (do-report {:type :end-behavior :string ~string } ))
  )

