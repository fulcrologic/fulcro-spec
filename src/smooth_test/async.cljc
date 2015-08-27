(ns smooth-test.async)

(defprotocol IAsyncQueue
  (advance-clock
    [this ms])
  (schedule-event
    [this ms-from-now fn-to-call])
  )

(defrecord Event [abs-time fn-to-call])

(defn process-first-event!
  "Triggers the first event in the queue (runs it), and removes it from the queue."
  [queue]
  (if-let [evt (first queue)]
    (do
      ((:fn-to-call evt))
      (swap! (:schedule queue) #(dissoc % (:abs-time evt))))))

(defrecord AsyncQueue [schedule now]
  IAsyncQueue
  (advance-clock
    ;"Move the clock forward by the specified number of ms, triggering events (even those added by interstitial triggers) in the correct order up to (and including) events that coincide with the final time."
    [this ms]
    (let [stop-time (+ ms @(:now this))]
      (loop [evt (first @(:schedule this))]
        (if (and evt (<= (:abs-time evt) stop-time))
          (do (process-first-event! this)
              (recur (first @(:schedule this))))
          )
        )
      (reset! (:now this) stop-time)
      )
    )
  (schedule-event
    ;"Schedule an event which should occur at some time in the future (offset from now)."
    [this ms-from-now fn-to-call]
    (let [tm (+ ms-from-now @(:now this))
          event (map->Event {:abs-time   tm
                             :fn-to-call fn-to-call})]
      (if (contains? @(:schedule this) tm)
        (throw (ex-data (str "Schedule already contains an event " ms-from-now "ms from 'now' which would generate an indeterminant ordering for your events. Please offset your submission time a bit")))
        (swap! (:schedule this) #(assoc % (:abs-time event) event)))
      )
    )
  )

(defn make-async-queue
  "Build an asynchronous event simulation queue."
  []
  (let [sort-by-event-time (fn [a b] (< (:abs-time a) (:abs-time b)))]
    (map->AsyncQueue {:now      (atom 0)
                      :schedule (atom (sorted-map-by sort-by-event-time))})))
