(ns fulcro-spec.examples.async-operations-spec
  "Comprehensive tests for async operations, including timeline testing.
  Demonstrates:
  - Testing pure retry/debounce logic without timeline
  - Testing async operations WITH timeline support
  - Verifying callback sequences
  - Testing exponential backoff"
  (:require
    [fulcro-spec.core :refer [specification behavior assertions when-mocking
                               with-timeline async tick]]
    [fulcro-spec.examples.async-operations :as async-ops]
    [fulcro-spec.mocking :as mock]))

;; ==============================================================================
;; Testing Pure Functions - No Timeline Needed
;; ==============================================================================

(specification "should-retry?" :group1
  (behavior "returns false when attempts >= max-attempts"
    (assertions
      (async-ops/should-retry? {:type :timeout} 3 3) => false
      (async-ops/should-retry? {:type :timeout} 5 3) => false))

  (behavior "returns true for retriable errors when attempts < max"
    (assertions
      (async-ops/should-retry? {:type :timeout} 1 3) => true
      (async-ops/should-retry? {:type :network-error} 0 3) => true
      (async-ops/should-retry? {:type :rate-limited} 2 3) => true))

  (behavior "returns false for non-retriable errors"
    (assertions
      (async-ops/should-retry? {:type :auth-error} 0 3) => false
      (async-ops/should-retry? {:type :not-found} 1 3) => false)))

(specification "calculate-backoff-delay" :group1
  (behavior "calculates exponential backoff"
    (assertions
      "first retry: 100ms * 2^0 = 100ms"
      (async-ops/calculate-backoff-delay 0 100) => 100.0
      "second retry: 100ms * 2^1 = 200ms"
      (async-ops/calculate-backoff-delay 1 100) => 200.0
      "third retry: 100ms * 2^2 = 400ms"
      (async-ops/calculate-backoff-delay 2 100) => 400.0
      "fourth retry: 100ms * 2^3 = 800ms"
      (async-ops/calculate-backoff-delay 3 100) => 800.0)))

(specification "should-execute-debounced?" :group1
  (behavior "returns true when elapsed >= debounce time"
    (assertions
      (async-ops/should-execute-debounced? 300 300) => true
      (async-ops/should-execute-debounced? 500 300) => true))

  (behavior "returns false when elapsed < debounce time"
    (assertions
      (async-ops/should-execute-debounced? 100 300) => false
      (async-ops/should-execute-debounced? 299 300) => false)))

;; ==============================================================================
;; Testing Retry State - Pure Data Transformations
;; ==============================================================================

(specification "make-retry-state" :group1
  (behavior "creates initial state with zero attempts"
    (let [state (async-ops/make-retry-state)]
      (assertions
        (:attempts state) => 0
        (:errors state) => []))))

(specification "update-retry-state" :group1
  (behavior "increments attempts and records error"
    (let [state (async-ops/make-retry-state)
          error {:type :timeout :message "Request timeout"}
          updated (async-ops/update-retry-state state error)]
      (assertions
        (:attempts updated) => 1
        (count (:errors updated)) => 1
        (first (:errors updated)) => error)))

  (behavior "accumulates multiple errors"
    (let [state (async-ops/make-retry-state)
          error1 {:type :timeout}
          error2 {:type :network-error}
          updated (-> state
                      (async-ops/update-retry-state error1)
                      (async-ops/update-retry-state error2))]
      (assertions
        (:attempts updated) => 2
        (count (:errors updated)) => 2))))

;; ==============================================================================
;; Testing Retry Plan - Pure Logic
;; ==============================================================================

(specification "make-retry-plan" :group2
  (behavior "returns immediate attempt when no errors"
    (let [plan (async-ops/make-retry-plan [] 3 100)]
      (assertions
        (:action plan) => :attempt
        (:delay-ms plan) => 0)))

  (behavior "returns retry with backoff for retriable error"
    (let [errors [{:type :timeout}]
          plan (async-ops/make-retry-plan errors 3 100)]
      (assertions
        (:action plan) => :retry
        "delay should be 2^1 * 100 = 200ms after first error"
        (:delay-ms plan) => 200.0)))

  (behavior "calculates correct backoff for multiple retries"
    (let [errors [{:type :timeout}
                  {:type :timeout}]
          plan (async-ops/make-retry-plan errors 3 100)]
      (assertions
        (:action plan) => :retry
        "delay should be 2^2 * 100 = 400ms after two errors"
        (:delay-ms plan) => 400.0)))

  (behavior "returns fail when max attempts reached"
    (let [errors [{:type :timeout}
                  {:type :timeout}
                  {:type :timeout}]
          plan (async-ops/make-retry-plan errors 3 100)]
      (assertions
        (:action plan) => :fail
        (:error plan) => {:type :timeout})))

  (behavior "returns fail immediately for non-retriable error"
    (let [errors [{:type :auth-error}]
          plan (async-ops/make-retry-plan errors 3 100)]
      (assertions
        (:action plan) => :fail
        (:error plan) => {:type :auth-error}))))

;; ==============================================================================
;; Testing Async Operations WITH Timeline Support
;; ==============================================================================

#?(:clj  ;; Timeline testing currently only works in CLJ
   (specification "retry-operation! with timeline" :group3
     (behavior "succeeds on first attempt"
       (with-timeline
         (let [result (atom nil)
               my-operation (fn [success-cb error-cb]
                              (success-cb {:data "success"}))]
           (when-mocking
             ;; Timeline mock for scheduling
             (schedule-fn! cb delay-ms) => (async delay-ms (cb))

             (async-ops/retry-operation!
               my-operation
               schedule-fn!
               (fn [id] nil)  ;; cancel-fn!
               3    ;; max-attempts
               100  ;; base-delay-ms
               #(reset! result {:status :success :data %})
               #(reset! result {:status :error :error %}))

             (assertions
               "succeeds without retry"
               (:status @result) => :success
               (get-in @result [:data :data]) => "success")))))

     (behavior "retries on retriable error then succeeds"
       (with-timeline
         (let [result (atom nil)
               call-count (atom 0)
               my-operation (fn [success-cb error-cb]
                              (swap! call-count inc)
                              (if (< @call-count 2)
                                (error-cb {:type :timeout})
                                (success-cb {:data "success"})))]
           (when-mocking
             ;; Timeline: schedule callback
             (schedule-fn! cb delay-ms) => (async delay-ms (cb))

             (async-ops/retry-operation!
               my-operation
               schedule-fn!
               (fn [id] nil)  ;; cancel-fn!
               3
               100
               #(reset! result {:status :success :data %})
               #(reset! result {:status :error :error %}))

             ;; First attempt fails immediately
             (assertions
               "first attempt hasn't triggered success yet"
               @result => nil)

             ;; Advance time past first backoff (100ms * 2^0 = 100ms)
             (tick 150)

             (assertions
               "after backoff, operation retried and succeeded"
               (:status @result) => :success
               "made 2 total attempts"
               @call-count => 2)))))

     (behavior "gives up after max attempts"
       (with-timeline
         (let [result (atom nil)
               call-count (atom 0)
               my-operation (fn [success-cb error-cb]
                              (swap! call-count inc)
                              (error-cb {:type :timeout :message "Failed"}))]
           (when-mocking
             (schedule-fn! cb delay-ms) => (async delay-ms (cb))

             (async-ops/retry-operation!
               my-operation
               schedule-fn!
               (fn [id] nil)
               3    ;; max attempts
               100
               #(reset! result {:status :success :data %})
               #(reset! result {:status :error :error %}))

             ;; First attempt fails immediately
             (tick 50)

             ;; Second attempt (after 100ms backoff)
             (tick 100)

             ;; Third attempt (after 200ms backoff)
             (tick 200)

             ;; Should give up now (3 attempts made)
             (assertions
               "gives up after 3 attempts"
               (:status @result) => :error
               @call-count => 3)))))

     (behavior "does not retry non-retriable errors"
       (with-timeline
         (let [result (atom nil)]
           (when-mocking
             (operation! success-cb error-cb) => (error-cb {:type :auth-error})

             (async-ops/retry-operation!
               operation!
               (fn [cb ms] (async ms (cb)))
               (fn [id] nil)
               3
               100
               #(reset! result {:status :success :data %})
               #(reset! result {:status :error :error %}))

             (assertions
               "fails immediately without retry"
               (:status @result) => :error
               (get-in @result [:error :type]) => :auth-error)))))))

#?(:clj
   (specification "debounced-operation! with timeline" :group3
     (behavior "executes only after quiet period"
       (with-timeline
         (let [call-count (atom 0)
               last-arg (atom nil)]
           (when-mocking
             ;; The actual operation we're debouncing
             (do-search! query) => (do
                                     (swap! call-count inc)
                                     (reset! last-arg query))

             ;; Timer functions
             (schedule-fn! cb delay-ms) => (async delay-ms (cb))
             (cancel-fn! id) => nil

             (let [debounced (async-ops/debounced-operation!
                               do-search!
                               schedule-fn!
                               cancel-fn!
                               300)] ;; 300ms debounce

               ;; Rapid calls
               (debounced "te")
               (tick 100)

               (debounced "tes")
               (tick 100)

               (debounced "test")

               (assertions
                 "hasn't executed yet"
                 @call-count => 0)

               ;; Wait for debounce period
               (tick 300)

               (assertions
                 "executes only once after quiet period"
                 @call-count => 1
                 "with the latest argument"
                 @last-arg => "test"))))))

     (behavior "resets timer on each call"
       (with-timeline
         (let [call-count (atom 0)]
           (when-mocking
             (do-search! query) => (swap! call-count inc)
             (schedule-fn! cb delay-ms) => (async delay-ms (cb))
             (cancel-fn! id) => nil

             (let [debounced (async-ops/debounced-operation!
                               do-search!
                               schedule-fn!
                               cancel-fn!
                               300)]

               (debounced "a")
               (tick 250)  ;; Almost at debounce time

               (debounced "ab")  ;; Resets timer
               (tick 250)  ;; Still not executed

               (assertions
                 "still hasn't executed"
                 @call-count => 0)

               (tick 50)  ;; Now 300ms since last call

               (assertions
                 "executes after final quiet period"
                 @call-count => 1))))))))

;; ==============================================================================
;; Testing the Testable Alternative Pattern
;; ==============================================================================

(specification "execute-retry-plan!" :group2
  (behavior "executes immediate attempt"
    (when-mocking
      (operation! success-cb error-cb) => nil

      (let [plan {:action :attempt :delay-ms 0}]
        (async-ops/execute-retry-plan!
          plan
          operation!
          (fn [cb ms] nil)  ;; timer-fn!
          identity              ;; success-callback
          identity              ;; error-callback
          (fn [] nil))          ;; retry-fn!

        (assertions
          "calls operation once"
          (count (mock/calls-of operation!)) => 1))))

  (behavior "schedules retry with delay"
    (when-mocking
      (schedule-fn! cb delay-ms) => nil

      (let [plan {:action :retry :delay-ms 200}
            retry-fn (fn [] :retry)]
        (async-ops/execute-retry-plan!
          plan
          (fn [& _] nil)        ;; operation!
          schedule-fn!
          identity
          identity
          retry-fn)

        (assertions
          "schedules callback"
          (count (mock/calls-of schedule-fn!)) => 1
          "with correct delay"
          (mock/spied-value schedule-fn! 0 'delay-ms) => 200
          "with retry function"
          (mock/spied-value schedule-fn! 0 'cb) => retry-fn))))

  (behavior "calls error callback on failure"
    (let [error-result (atom nil)]
      (when-mocking
        (error-callback error) => (reset! error-result error)

        (let [plan {:action :fail :error {:type :timeout}}]
          (async-ops/execute-retry-plan!
            plan
            (fn [& _] nil)
            (fn [& _] nil)
            identity
            error-callback
            (fn [] nil))

          (assertions
            @error-result => {:type :timeout}))))))
