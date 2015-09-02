(ns smooth-test.async
  #?(:cljs (:require [cljs.pprint :refer [pprint]])
     :clj
           (:require [clojure.pprint :refer [pprint]]))
  )

(defprotocol IAsyncQueue
  (current-time [this] "Returns the current time on the simulated clock, in ms")
  (peek-event [this] "Returns the first event on the queue")
  (advance-clock [this ms]
    "Move the clock forward by the specified number of ms, triggering events (even those added by interstitial triggers) in the correct order up to (and including) events that coincide with the final time.")
  (schedule-event [this ms-from-now fn-to-call]
    "Schedule an event which should occur at some time in the future (offset from now).")
  )

(defrecord Event [abs-time fn-to-call])

(defn process-first-event!
  "Triggers the first event in the queue (runs it), and removes it from the queue."
  [queue]
  (if-let [evt (peek-event queue)]
    (do
      ((:fn-to-call evt))
      (swap! (:schedule queue) #(dissoc % (:abs-time evt)))
      )))

(defrecord AsyncQueue [schedule now]
  IAsyncQueue
  (current-time [this] @(:now this))
  (peek-event [this] (second (first @(-> this :schedule))))
  (advance-clock
    [this ms]
    (let [stop-time (+ ms @(:now this))]
      (loop [evt (peek-event this)]
        (let [now (or (:abs-time evt) (inc stop-time))]
          (if (<= now stop-time)
            (do
              (reset! (:now this) now)
              (process-first-event! this)
              (recur (peek-event this)))
            ))
        )
      (reset! (:now this) stop-time)
      )
    )
  (schedule-event
    [this ms-from-now fn-to-call]
    (let [tm (+ ms-from-now @(:now this))
          event (Event. tm fn-to-call)]
      (if (contains? @(:schedule this) tm)
        (throw (ex-info (str "Schedule already contains an event " ms-from-now "ms from 'now' which would generate an indeterminant ordering for your events. Please offset your submission time a bit") {}))
        (swap! (:schedule this) #(assoc % (:abs-time event) event)))
      )
    )
  )

(defn make-async-queue
  "Build an asynchronous event simulation queue."
  []
  (AsyncQueue. (atom (sorted-map)) (atom 0)))




