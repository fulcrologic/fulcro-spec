(ns smooth-test.timeline
  (:require [smooth-test.async :as async]
    )
  )

(defmacro with-timeline [& forms]
  `(let [~'*async-queue* (async/make-async-queue)]
     ~@forms
     )
  )

(defmacro event [tm cb]
  `(async/schedule-event ~'*async-queue* ~tm (fn [] ~cb))
  )

(defmacro tick [tm]
  `(async/advance-clock ~'*async-queue* ~tm)
  )
