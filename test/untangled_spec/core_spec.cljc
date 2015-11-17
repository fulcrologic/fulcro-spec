(ns untangled-spec.core-spec
  #?(:clj
      (:require [untangled-spec.core :as c :refer [specification behavior provided assertions]]
                [clojure.test :as t :refer (are is deftest with-test run-tests testing do-report)]
                ))
  #?(:clj
      (:import clojure.lang.ExceptionInfo))
  )

#?(:clj
    (specification "untangled-spec.core-spec"
                   (behavior "assertions"
                             )
                   )
    )
