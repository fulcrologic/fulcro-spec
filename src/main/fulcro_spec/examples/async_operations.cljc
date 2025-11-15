(ns fulcro-spec.examples.async-operations
  "Examples demonstrating asynchronous operations and how to test them.
  This module shows patterns for:
  - Callback-based async operations
  - Debouncing and throttling
  - Retry logic
  - Timeout handling"
  (:require
    [clojure.string :as str]))

;; ==============================================================================
;; Pure Functions - Retry Logic
;; ==============================================================================

(defn should-retry?
  "Pure: Determines if an operation should be retried.
  BEHAVIOR 1: Returns false if attempts >= max-attempts
  BEHAVIOR 2: Returns true if error is retriable
  BEHAVIOR 3: Returns false if error is not retriable"
  [error attempts max-attempts]
  (and (< attempts max-attempts)
       (#{:timeout :network-error :rate-limited} (:type error))))

(defn calculate-backoff-delay
  "Pure: Calculates exponential backoff delay.
  BEHAVIOR: Returns delay-ms * (2 ^ attempt)"
  [attempt base-delay-ms]
  (* base-delay-ms (Math/pow 2 attempt)))

;; ==============================================================================
;; Pure Functions - Debouncing
;; ==============================================================================

(defn should-execute-debounced?
  "Pure: Determines if debounced action should execute.
  BEHAVIOR 1: Returns true if elapsed-ms >= debounce-ms
  BEHAVIOR 2: Returns false if elapsed-ms < debounce-ms"
  [elapsed-ms debounce-ms]
  (>= elapsed-ms debounce-ms))

;; ==============================================================================
;; Async Operations (Side Effect Interfaces)
;; ==============================================================================

(defn schedule-timeout!
  "Side effect: Schedules a function to run after delay-ms.
  Returns a timer ID that can be used to cancel."
  [timer-fn! callback delay-ms]
  (timer-fn! callback delay-ms))

(defn cancel-timeout!
  "Side effect: Cancels a scheduled timeout."
  [cancel-fn! timer-id]
  (cancel-fn! timer-id))

(defn api-call!
  "Side effect: Makes an API call, returns result to callback."
  [http-fn! endpoint data callback]
  (http-fn! endpoint data callback))

;; ==============================================================================
;; Stateful Operations - These maintain state and are harder to test
;; ==============================================================================

(defn make-retry-state
  "Creates initial retry state.
  BEHAVIOR: Returns map with attempt count and error history"
  []
  {:attempts 0
   :errors []})

(defn update-retry-state
  "Pure: Updates retry state after an attempt.
  BEHAVIOR: Increments attempts and records error"
  [state error]
  (-> state
      (update :attempts inc)
      (update :errors conj error)))

;; ==============================================================================
;; Orchestration - Combining Async Operations with Logic
;; ==============================================================================

(defn retry-operation!
  "Performs an operation with exponential backoff retry.
  This is a stateful async operation that's challenging to test.

  BEHAVIOR 1: Attempts operation immediately
  BEHAVIOR 2: On retriable error, schedules retry with backoff
  BEHAVIOR 3: On non-retriable error, calls error-callback
  BEHAVIOR 4: On success, calls success-callback
  BEHAVIOR 5: After max attempts, calls error-callback"
  [operation! timer-fn! cancel-fn! max-attempts base-delay-ms success-callback error-callback]
  (let [state (atom (make-retry-state))]
    (letfn [(attempt []
              (operation!
                ;; Success handler
                (fn [result]
                  (success-callback result))
                ;; Error handler
                (fn [error]
                  (let [current-state (swap! state update-retry-state error)
                        attempts (:attempts current-state)]
                    (if (should-retry? error attempts max-attempts)
                      (let [delay (calculate-backoff-delay attempts base-delay-ms)]
                        (schedule-timeout! timer-fn! attempt delay))
                      (error-callback error))))))]
      (attempt))))

(defn debounced-operation!
  "Creates a debounced version of an operation.
  Returns a function that, when called, will only execute after
  debounce-ms milliseconds of inactivity.

  BEHAVIOR 1: Cancels previous timer if called again
  BEHAVIOR 2: Schedules operation after debounce-ms
  BEHAVIOR 3: Only executes latest call after quiet period"
  [operation! timer-fn! cancel-fn! debounce-ms]
  (let [timer-id (atom nil)]
    (fn [& args]
      ;; Cancel existing timer
      (when-let [id @timer-id]
        (cancel-timeout! cancel-fn! id))
      ;; Schedule new execution
      (reset! timer-id
        (schedule-timeout!
          timer-fn!
          #(apply operation! args)
          debounce-ms)))))

;; ==============================================================================
;; Simpler Testable Alternatives
;; ==============================================================================
;; The above functions are realistic but hard to test without timeline support.
;; Here's an alternative pattern that's easier to test:

(defn make-retry-plan
  "Pure: Creates a plan for retrying an operation.
  This separates the retry logic from the async execution.

  BEHAVIOR 1: If no errors yet, includes immediate attempt
  BEHAVIOR 2: If retriable error, includes retry with backoff delay
  BEHAVIOR 3: If non-retriable error or max attempts, returns failure plan"
  [error-history max-attempts base-delay-ms]
  (let [attempts (count error-history)
        last-error (last error-history)]
    (cond
      ;; First attempt or no error yet
      (nil? last-error)
      {:action :attempt
       :delay-ms 0}

      ;; Should retry
      (should-retry? last-error attempts max-attempts)
      {:action :retry
       :delay-ms (calculate-backoff-delay attempts base-delay-ms)}

      ;; Give up
      :else
      {:action :fail
       :error last-error})))

(defn execute-retry-plan!
  "Executes a retry plan.
  This is a thin side-effect layer over the pure retry logic."
  [plan operation! timer-fn! success-callback error-callback retry-fn!]
  (case (:action plan)
    :attempt
    (operation! success-callback error-callback)

    :retry
    (schedule-timeout! timer-fn! retry-fn! (:delay-ms plan))

    :fail
    (error-callback (:error plan))))
