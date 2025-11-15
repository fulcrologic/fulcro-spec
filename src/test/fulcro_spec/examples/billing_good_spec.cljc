(ns fulcro-spec.examples.billing-good-spec
  "Comprehensive test suite for the well-structured billing code.
  This demonstrates how EASY it is to test properly factored code."
  (:require
    [fulcro-spec.core :refer [specification behavior assertions when-mocking]]
    [fulcro-spec.examples.billing-good :as bg]
    [fulcro-spec.mocking :as mock]))

;; ==============================================================================
;; Testing Pure Functions - No Mocking Needed!
;; ==============================================================================

(specification "subscription-tier-discount" :group1
  (behavior "returns 1.0 for basic tier"
    (assertions
      (bg/subscription-tier-discount "basic") => 1.0))

  (behavior "returns 0.9 for premium tier"
    (assertions
      (bg/subscription-tier-discount "premium") => 0.9))

  (behavior "returns 0.8 for enterprise tier"
    (assertions
      (bg/subscription-tier-discount "enterprise") => 0.8))

  (behavior "returns 1.0 for unknown tier"
    (assertions
      (bg/subscription-tier-discount "unknown") => 1.0
      (bg/subscription-tier-discount nil) => 1.0)))

(specification "annual-discount-multiplier" :group1
  (behavior "always returns 0.95 (5% discount)"
    (assertions
      (bg/annual-discount-multiplier) => 0.95)))

(specification "calculate-subscription-pricing" :group1
  (behavior "applies tier discount and calculates annual price"
    (behavior "for basic tier"
      (assertions
        (bg/calculate-subscription-pricing "basic" 100)
        => {:monthly-price 100.0
            :annual-price (* 100.0 12 0.95)}))

    (behavior "for premium tier"
      (assertions
        (bg/calculate-subscription-pricing "premium" 100)
        => {:monthly-price 90.0
            :annual-price (* 90.0 12 0.95)}))

    (behavior "for enterprise tier"
      (assertions
        (bg/calculate-subscription-pricing "enterprise" 100)
        => {:monthly-price 80.0
            :annual-price (* 80.0 12 0.95)}))))

;; ==============================================================================
;; Testing Date/Time Logic - Pure Functions
;; ==============================================================================

(specification "days-between" :group1
  (behavior "calculates days between two dates"
    (let [start #inst "2025-01-01T00:00:00"
          end #inst "2025-01-31T00:00:00"]
      (assertions
        (bg/days-between start end) => 30.0))))

(specification "billing-due?" :group1
  (behavior "returns true when never billed"
    (assertions
      (bg/billing-due? nil #inst "2025-01-15") => true))

  (behavior "returns true when >= 30 days since last billing"
    (let [last-billed #inst "2024-12-01T00:00:00"
          current #inst "2025-01-15T00:00:00"]
      (assertions
        (bg/billing-due? last-billed current) => true)))

  (behavior "returns false when < 30 days since last billing"
    (let [last-billed #inst "2025-01-01T00:00:00"
          current #inst "2025-01-15T00:00:00"]
      (assertions
        (bg/billing-due? last-billed current) => false))))

(specification "day-of-week helpers" :group1
  (behavior "tuesday? returns true only for day 2"
    (assertions
      (bg/tuesday? 2) => true
      (bg/tuesday? 0) => false
      (bg/tuesday? 1) => false
      (bg/tuesday? 3) => false))

  (behavior "sunday? returns true only for day 0"
    (assertions
      (bg/sunday? 0) => true
      (bg/sunday? 1) => false
      (bg/sunday? 2) => false))

  (behavior "weekday? returns true for Mon/Wed/Fri"
    (assertions
      (bg/weekday? 1) => true
      (bg/weekday? 3) => true
      (bg/weekday? 5) => true
      (bg/weekday? 0) => false
      (bg/weekday? 2) => false
      (bg/weekday? 4) => false
      (bg/weekday? 6) => false)))

;; ==============================================================================
;; Testing Business Logic - Pure Functions
;; ==============================================================================

(specification "sufficient-balance?" :group1
  (behavior "returns true when balance >= amount"
    (assertions
      (bg/sufficient-balance? 100 50) => true
      (bg/sufficient-balance? 100 100) => true))

  (behavior "returns false when balance < amount"
    (assertions
      (bg/sufficient-balance? 50 100) => false)))

(specification "deduct-from-balance" :group2
  (behavior "subtracts amount from balance"
    (assertions
      (bg/deduct-from-balance 100 30) => 70
      (bg/deduct-from-balance 50 50) => 0)))

(specification "billing-success-result" :group2
  (behavior "creates success result map"
    (assertions
      (bg/billing-success-result 30 70)
      => {:status :success
          :amount 30
          :new-balance 70})))

(specification "billing-failure-result" :group2
  (behavior "creates failure result map"
    (assertions
      (bg/billing-failure-result 100 50)
      => {:status :insufficient-funds
          :required 100
          :available 50})))

(specification "calculate-billing-result" :group2
  (behavior "returns success when balance is sufficient"
    (assertions
      (bg/calculate-billing-result 100 30)
      => {:status :success
          :amount 30
          :new-balance 70}))

  (behavior "returns failure when balance is insufficient"
    (assertions
      (bg/calculate-billing-result 50 100)
      => {:status :insufficient-funds
          :required 100
          :available 50})))

;; ==============================================================================
;; Testing Message Generation - Pure Functions
;; ==============================================================================

(specification "billing-success-message" :group2
  (behavior "generates formatted success message"
    (assertions
      (bg/billing-success-message 30 70)
      => "We have charged $30 to your account. Your remaining balance is $70. Thank you for your subscription!")))

(specification "billing-failure-message" :group2
  (behavior "generates formatted failure message"
    (assertions
      (bg/billing-failure-message 100 50)
      => "We were unable to charge $100 to your account. Your current balance is $50. Please add funds to avoid service interruption.")))

(specification "billing-email-content" :group2
  (behavior "generates success email for success result"
    (let [result {:status :success :amount 30 :new-balance 70}]
      (assertions
        (bg/billing-email-content result)
        => {:subject "Billing Successful"
            :body "We have charged $30 to your account. Your remaining balance is $70. Thank you for your subscription!"})))

  (behavior "generates failure email for insufficient-funds result"
    (let [result {:status :insufficient-funds :required 100 :available 50}]
      (assertions
        (bg/billing-email-content result)
        => {:subject "Billing Failed"
            :body "We were unable to charge $100 to your account. Your current balance is $50. Please add funds to avoid service interruption."}))))

;; ==============================================================================
;; Testing Data Updates - Pure Functions
;; ==============================================================================

(specification "billing-db-updates" :group3
  (behavior "returns updates for successful billing"
    (let [result {:status :success :amount 30 :new-balance 70}
          date #inst "2025-01-15T00:00:00"]
      (assertions
        (bg/billing-db-updates 123 result date)
        => [[:balance 123 70]
            [:last-billed 123 date]])))

  (behavior "returns empty list for failed billing"
    (let [result {:status :insufficient-funds :required 100 :available 50}
          date #inst "2025-01-15T00:00:00"]
      (assertions
        (bg/billing-db-updates 123 result date)
        => []))))

;; ==============================================================================
;; Testing High-Level Orchestration - Pure Functions with Composition
;; ==============================================================================

(specification "billing-action-plan" :group3
  (behavior "returns nil when user is nil"
    (assertions
      (bg/billing-action-plan {:user nil
                               :subscription {:monthly-price 30}
                               :balance 100
                               :current-date #inst "2025-01-15"})
      => nil))

  (behavior "returns nil when billing is not due"
    (let [data {:user {:id 123
                       :email "user@example.com"
                       :last-billed #inst "2025-01-01T00:00:00"}
                :subscription {:monthly-price 30}
                :balance 100
                :current-date #inst "2025-01-15T00:00:00"}] ;; Only 14 days later
      (assertions
        (bg/billing-action-plan data) => nil)))

  (behavior "returns success plan when billing is due and funds sufficient"
    (let [data {:user {:id 123
                       :email "user@example.com"
                       :last-billed nil}
                :subscription {:monthly-price 30}
                :balance 100
                :current-date #inst "2025-01-15T00:00:00"}
          plan (bg/billing-action-plan data)]
      (assertions
        "billing result is success"
        (:billing-result plan) => {:status :success
                                    :amount 30
                                    :new-balance 70}
        "email has correct recipient"
        (get-in plan [:email :to]) => "user@example.com"
        "email has success subject"
        (get-in plan [:email :subject]) => "Billing Successful"
        "email body contains amount"
        (get-in plan [:email :body])
        => "We have charged $30 to your account. Your remaining balance is $70. Thank you for your subscription!"
        "db updates include balance and last-billed"
        (:db-updates plan) => [[:balance 123 70]
                                [:last-billed 123 #inst "2025-01-15T00:00:00"]])))

  (behavior "returns failure plan when billing is due and funds insufficient"
    (let [data {:user {:id 123
                       :email "user@example.com"
                       :last-billed nil}
                :subscription {:monthly-price 100}
                :balance 50
                :current-date #inst "2025-01-15T00:00:00"}
          plan (bg/billing-action-plan data)]
      (assertions
        "billing result is failure"
        (:billing-result plan) => {:status :insufficient-funds
                                    :required 100
                                    :available 50}
        "email has failure subject"
        (get-in plan [:email :subject]) => "Billing Failed"
        "db updates are empty"
        (:db-updates plan) => []))))

;; ==============================================================================
;; Testing Scheduling Logic - Pure Functions
;; ==============================================================================

(specification "tasks-for-day" :group3
  (behavior "returns [:billing] for Tuesday"
    (assertions
      (bg/tasks-for-day 2) => [:billing]))

  (behavior "returns [:cleanup] for Sunday"
    (assertions
      (bg/tasks-for-day 0) => [:cleanup]))

  (behavior "returns [:report] for weekdays (Mon/Wed/Fri)"
    (assertions
      (bg/tasks-for-day 1) => [:report]
      (bg/tasks-for-day 3) => [:report]
      (bg/tasks-for-day 5) => [:report]))

  (behavior "returns [] for other days"
    (assertions
      (bg/tasks-for-day 4) => []
      (bg/tasks-for-day 6) => [])))

(specification "daily-task-plan" :group4
  (behavior "creates plan with tasks for the day"
    ;; Mock day-of-week to control the test
    (when-mocking
      (bg/day-of-week date) => 2 ;; Tuesday

      (let [date #inst "2025-01-15T00:00:00"
            plan (bg/daily-task-plan date)]
        (assertions
          (:date plan) => date
          (:day-of-week plan) => 2
          (:tasks plan) => [:billing])))))

;; ==============================================================================
;; Testing Validation - Pure Functions with Error Handling
;; ==============================================================================

(specification "valid-tier?" :group4
  (behavior "returns true for valid tiers"
    (assertions
      (bg/valid-tier? "basic") => true
      (bg/valid-tier? "premium") => true
      (bg/valid-tier? "enterprise") => true))

  (behavior "returns false for invalid tiers"
    (assertions
      (bg/valid-tier? "invalid") => false
      (bg/valid-tier? nil) => false)))

(specification "parse-price" :group4
  (behavior "parses valid price strings"
    (assertions
      (bg/parse-price "100") => 100.0
      (bg/parse-price "99.99") => 99.99))

  (behavior "returns nil for invalid price strings"
    (assertions
      (bg/parse-price "not-a-number") => nil
      (bg/parse-price "") => nil))

  (behavior "returns nil for non-string input"
    (assertions
      (bg/parse-price 100) => nil
      (bg/parse-price nil) => nil)))

(specification "non-negative?" :group4
  (behavior "returns true for non-negative numbers"
    (assertions
      (bg/non-negative? 0) => true
      (bg/non-negative? 100) => true
      (bg/non-negative? 0.01) => true))

  (behavior "returns false for negative numbers"
    (assertions
      (bg/non-negative? -1) => false
      (bg/non-negative? -0.01) => false))

  (behavior "returns false for non-numbers"
    (assertions
      (bg/non-negative? "100") => false
      (bg/non-negative? nil) => false)))

(specification "validate-subscription-data" :group5
  (behavior "returns error for non-map input"
    (assertions
      (bg/validate-subscription-data "not-a-map")
      => {:type :invalid-type}))

  (behavior "returns error for invalid tier"
    (assertions
      (bg/validate-subscription-data {:tier "invalid" :price "100"})
      => {:type :invalid-tier :tier "invalid"}))

  (behavior "returns error for non-string price"
    (assertions
      (bg/validate-subscription-data {:tier "basic" :price 100})
      => {:type :invalid-price}))

  (behavior "returns error for unparseable price"
    (assertions
      (bg/validate-subscription-data {:tier "basic" :price "not-a-number"})
      => {:type :parse-error :price "not-a-number"}))

  (behavior "returns error for negative price"
    (assertions
      (bg/validate-subscription-data {:tier "basic" :price "-10"})
      => {:type :negative-price}))

  (behavior "returns nil for valid data"
    (assertions
      (bg/validate-subscription-data {:tier "basic" :price "100"})
      => nil)))

(specification "transform-subscription-data" :group5
  (behavior "transforms valid subscription data"
    (assertions
      (bg/transform-subscription-data {:tier "premium" :price "100"})
      => {:tier "premium"
          :monthly-price 90.0
          :annual-price (* 90.0 12 0.95)})))

(specification "validate-and-transform-subscription" :group5
  (behavior "throws for invalid data"
    (assertions
      (bg/validate-and-transform-subscription {:tier "invalid" :price "100"})
      =throws=> #?(:clj Exception :cljs js/Error)))

  (behavior "returns transformed data for valid input"
    (assertions
      (bg/validate-and-transform-subscription {:tier "basic" :price "100"})
      => {:tier "basic"
          :monthly-price 100.0
          :annual-price (* 100.0 12 0.95)})))

;; ==============================================================================
;; Testing Functions with Side Effects - Using Mocks
;; ==============================================================================

(specification "process-billing!" :group5
  (behavior "successfully processes billing when conditions are met"
    ;; Mock all the side effect functions
    (when-mocking
      (bg/fetch-user db user-id) => {:id 123
                                     :email "user@example.com"
                                     :subscription-id 456
                                     :last-billed nil}
      (bg/fetch-subscription db sub-id) => {:monthly-price 30}
      (bg/fetch-balance db user-id) => 100
      (bg/get-current-time clock) => #inst "2025-01-15T00:00:00"
      (bg/update-db! db! k v) => nil
      (bg/send-email! email-svc to subj body) => nil

      (let [result (bg/process-billing! 123 :db :db! :email-svc :clock)]
        (assertions
          "returns success result"
          result => {:status :success
                     :amount 30
                     :new-balance 70}

          "updates balance in database"
          (mock/spied-value bg/update-db! 0 'k) => [:balance 123]
          (mock/spied-value bg/update-db! 0 'v) => 70

          "updates last-billed in database"
          (mock/spied-value bg/update-db! 1 'k) => [:last-billed 123]
          (mock/spied-value bg/update-db! 1 'v) => #inst "2025-01-15T00:00:00"

          "sends success email"
          (mock/spied-value bg/send-email! 0 'to) => "user@example.com"
          (mock/spied-value bg/send-email! 0 'subj) => "Billing Successful"))))

  (behavior "returns nil when user not found"
    (when-mocking
      (bg/fetch-user db user-id) => nil

      (assertions
        (bg/process-billing! 123 :db :db! :email-svc :clock) => nil)))

  (behavior "returns nil when billing is not due"
    (when-mocking
      (bg/fetch-user db user-id) => {:id 123
                                     :email "user@example.com"
                                     :subscription-id 456
                                     :last-billed #inst "2025-01-01T00:00:00"}
      (bg/fetch-subscription db sub-id) => {:monthly-price 30}
      (bg/fetch-balance db user-id) => 100
      (bg/get-current-time clock) => #inst "2025-01-15T00:00:00" ;; Only 14 days

      (assertions
        (bg/process-billing! 123 :db :db! :email-svc :clock) => nil))))

(specification "run-daily-tasks!" :group5
  (behavior "runs billing on Tuesday"
    (when-mocking
      (bg/get-current-time clock) =1x=> #inst "2025-01-14T00:00:00" ;; Tuesday
      (bg/day-of-week date) => 2
      (bg/process-billing! user-id db! email-svc clock) => nil

      (bg/run-daily-tasks! :db! :email-svc :clock)

      (assertions
        "calls process-billing! twice"
        (count (mock/calls-of bg/process-billing!)) => 2
        "first call is for user 123"
        (mock/spied-value bg/process-billing! 0 'user-id) => 123
        "second call is for user 456"
        (mock/spied-value bg/process-billing! 1 'user-id) => 456)))

  (behavior "runs cleanup on Sunday"
    (when-mocking
      (bg/get-current-time clock) => #inst "2025-01-12T00:00:00" ;; Sunday
      (bg/day-of-week date) => 0
      (bg/update-db! db! k v) => nil

      (bg/run-daily-tasks! :db! :email-svc :clock)

      (assertions
        "updates cleanup-run"
        (mock/spied-value bg/update-db! 0 'k) => [:cleanup-run])))

  (behavior "sends report on weekdays"
    (when-mocking
      (bg/get-current-time clock) => #inst "2025-01-13T00:00:00" ;; Monday
      (bg/day-of-week date) => 1
      (bg/send-email! email-svc to subj body) => nil

      (bg/run-daily-tasks! :db! :email-svc :clock)

      (assertions
        "sends email to admin"
        (mock/spied-value bg/send-email! 0 'to) => "admin@example.com"
        "subject is Daily Report"
        (mock/spied-value bg/send-email! 0 'subj) => "Daily Report"))))
