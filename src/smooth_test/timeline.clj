(ns smooth-test.timeline
  (:require [smooth-test.async :as async]
    )
  )

(defmacro async [tm cb]
  `(async/schedule-event ~'*async-queue* ~tm (fn [] ~cb))
  )

(defmacro with-timeline [& forms]
  `(let [~'*async-queue* (async/make-async-queue)]
     ~@forms
     )
  )

(defmacro tick [tm]
  `(async/advance-clock ~'*async-queue* ~tm)
  )


