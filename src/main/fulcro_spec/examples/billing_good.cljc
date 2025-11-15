(ns fulcro-spec.examples.billing-good
  "Example of WELL-STRUCTURED code that is easy to test.
  This demonstrates best practices:
  - Side effects isolated at the edges
  - Pure functions for business logic
  - Single level of abstraction per function
  - Easy to mock dependencies
  - Every behavior is easily testable"
  (:require
    [clojure.string :as str]))

;; ==============================================================================
;; Domain Types and Pure Data Transformations
;; ==============================================================================

(defn subscription-tier-discount
  "Pure function: Given a tier, returns the discount multiplier.
  BEHAVIOR 1: basic tier gets no discount (1.0)
  BEHAVIOR 2: premium tier gets 10% discount (0.9)
  BEHAVIOR 3: enterprise tier gets 20% discount (0.8)
  BEHAVIOR 4: unknown tiers default to no discount (1.0)"
  [tier]
  (case tier
    "basic" 1.0
    "premium" 0.9
    "enterprise" 0.8
    1.0))

(defn annual-discount-multiplier
  "Pure function: Returns the annual billing discount.
  BEHAVIOR: Always returns 0.95 (5% discount)"
  []
  0.95)

(defn calculate-subscription-pricing
  "Pure function: Calculates monthly and annual prices.
  BEHAVIOR 1: Applies tier discount to base price
  BEHAVIOR 2: Calculates annual price with annual discount

  This function has TWO behaviors (tier pricing + annual pricing),
  making it easy to test all combinations."
  [tier base-price]
  (let [tier-multiplier (subscription-tier-discount tier)
        monthly-price (* base-price tier-multiplier)
        annual-price (* monthly-price 12 (annual-discount-multiplier))]
    {:monthly-price monthly-price
     :annual-price annual-price}))

;; ==============================================================================
;; Date and Time Logic (Pure Functions)
;; ==============================================================================

(defn days-between
  "Pure function: Calculate days between two dates.
  BEHAVIOR: Returns the number of days between start and end dates"
  [start-date end-date]
  (/ (- (.getTime end-date)
       (.getTime start-date))
    (* 1000.0 60 60 24)))

(defn billing-due?
  "Pure function: Determines if billing is due.
  BEHAVIOR 1: If never billed (nil last-billed), then due
  BEHAVIOR 2: If >= 30 days since last billing, then due
  BEHAVIOR 3: Otherwise, not due"
  [last-billed current-date]
  (or (nil? last-billed)
      (>= (days-between last-billed current-date) 30)))

(defn day-of-week
  "Pure function: Extracts day of week from a date.
  BEHAVIOR: Returns 0-6 (Sunday=0, Monday=1, etc.)"
  [date]
  (.getDay date))

(defn tuesday?
  "Pure function: Checks if a day-of-week value is Tuesday.
  BEHAVIOR: Returns true if day-of-week is 2"
  [day-of-week]
  (= day-of-week 2))

(defn sunday?
  "Pure function: Checks if a day-of-week value is Sunday.
  BEHAVIOR: Returns true if day-of-week is 0"
  [day-of-week]
  (= day-of-week 0))

(defn weekday?
  "Pure function: Checks if a day-of-week value is a weekday (Mon/Wed/Fri).
  BEHAVIOR: Returns true if day-of-week is 1, 3, or 5"
  [day-of-week]
  (boolean (#{1 3 5} day-of-week)))

;; ==============================================================================
;; Business Logic (Pure Functions)
;; ==============================================================================

(defn sufficient-balance?
  "Pure function: Checks if balance is sufficient for amount.
  BEHAVIOR: Returns true if balance >= amount"
  [balance amount]
  (>= balance amount))

(defn deduct-from-balance
  "Pure function: Calculates new balance after deduction.
  BEHAVIOR: Returns balance - amount"
  [balance amount]
  (- balance amount))

(defn billing-success-result
  "Pure function: Creates a success result.
  BEHAVIOR: Returns map with :success status and details"
  [amount new-balance]
  {:status :success
   :amount amount
   :new-balance new-balance})

(defn billing-failure-result
  "Pure function: Creates a failure result.
  BEHAVIOR: Returns map with :insufficient-funds status and details"
  [required available]
  {:status :insufficient-funds
   :required required
   :available available})

(defn calculate-billing-result
  "Pure function: Determines billing result based on balance and amount.
  BEHAVIOR 1: If sufficient balance, return success result with new balance
  BEHAVIOR 2: If insufficient balance, return failure result with details"
  [balance amount]
  (if (sufficient-balance? balance amount)
    (billing-success-result amount (deduct-from-balance balance amount))
    (billing-failure-result amount balance)))

;; ==============================================================================
;; Message Generation (Pure Functions)
;; ==============================================================================

(defn billing-success-message
  "Pure function: Generates success email message.
  BEHAVIOR: Returns formatted success message"
  [amount new-balance]
  (str "We have charged $" amount " to your account. "
    "Your remaining balance is $" new-balance ". "
    "Thank you for your subscription!"))

(defn billing-failure-message
  "Pure function: Generates failure email message.
  BEHAVIOR: Returns formatted failure message"
  [amount balance]
  (str "We were unable to charge $" amount " to your account. "
    "Your current balance is $" balance ". "
    "Please add funds to avoid service interruption."))

(defn billing-email-content
  "Pure function: Determines email subject and body based on result.
  BEHAVIOR 1: For success status, returns success subject and message
  BEHAVIOR 2: For insufficient-funds status, returns failure subject and message"
  [billing-result]
  (case (:status billing-result)
    :success
    {:subject "Billing Successful"
     :body (billing-success-message
             (:amount billing-result)
             (:new-balance billing-result))}

    :insufficient-funds
    {:subject "Billing Failed"
     :body (billing-failure-message
             (:required billing-result)
             (:available billing-result))}))

;; ==============================================================================
;; Data Updates (Pure Functions)
;; ==============================================================================

(defn billing-db-updates
  "Pure function: Determines what database updates are needed.
  BEHAVIOR 1: For success, returns balance and last-billed updates
  BEHAVIOR 2: For failure, returns empty list (no updates)"
  [user-id billing-result current-date]
  (case (:status billing-result)
    :success
    [[:balance user-id (:new-balance billing-result)]
     [:last-billed user-id current-date]]

    :insufficient-funds
    []))

;; ==============================================================================
;; High-Level Orchestration (Composing Pure Functions)
;; ==============================================================================

(defn billing-action-plan
  "Pure function: Creates a complete action plan for billing.
  This is a HIGHER level of abstraction that composes lower-level pure functions.

  BEHAVIOR 1: If user not found, returns nil
  BEHAVIOR 2: If billing not due, returns nil
  BEHAVIOR 3: If billing due and sufficient funds, returns success plan
  BEHAVIOR 4: If billing due and insufficient funds, returns failure plan

  Note: This function DESCRIBES what should happen but doesn't DO it.
  The side effects are pushed to the caller."
  [{:keys [user subscription balance current-date]}]
  (when user
    (when (billing-due? (:last-billed user) current-date)
      (let [amount (:monthly-price subscription)
            billing-result (calculate-billing-result balance amount)
            email-content (billing-email-content billing-result)
            db-updates (billing-db-updates (:id user) billing-result current-date)]
        {:billing-result billing-result
         :email {:to (:email user)
                 :subject (:subject email-content)
                 :body (:body email-content)}
         :db-updates db-updates}))))

;; ==============================================================================
;; Scheduling Logic (Pure Functions)
;; ==============================================================================

(defn tasks-for-day
  "Pure function: Returns list of tasks that should run on given day-of-week.
  BEHAVIOR 1: On Tuesday (2), returns [:billing]
  BEHAVIOR 2: On Sunday (0), returns [:cleanup]
  BEHAVIOR 3: On Mon/Wed/Fri (1/3/5), returns [:report]
  BEHAVIOR 4: On other days, returns []"
  [day-of-week]
  (cond
    (tuesday? day-of-week) [:billing]
    (sunday? day-of-week) [:cleanup]
    (weekday? day-of-week) [:report]
    :else []))

(defn daily-task-plan
  "Pure function: Creates plan for daily tasks.
  BEHAVIOR: Returns a plan with tasks based on current date's day-of-week"
  [current-date]
  (let [dow (day-of-week current-date)
        tasks (tasks-for-day dow)]
    {:date current-date
     :day-of-week dow
     :tasks tasks}))

;; ==============================================================================
;; Validation (Pure Functions)
;; ==============================================================================

(defn valid-tier?
  "Pure function: Checks if tier is valid.
  BEHAVIOR 1: Returns true for basic, premium, enterprise
  BEHAVIOR 2: Returns false for anything else"
  [tier]
  (contains? #{"basic" "premium" "enterprise"} tier))

(defn parse-price
  "Pure function: Parses price string to number.
  BEHAVIOR 1: Returns number if valid
  BEHAVIOR 2: Returns nil if invalid"
  [price-str]
  (when (string? price-str)
    (try
      #?(:clj (Double/parseDouble price-str)
         :cljs (js/parseFloat price-str))
      (catch #?(:clj Exception :cljs js/Error) _
        nil))))

(defn non-negative?
  "Pure function: Checks if number is non-negative.
  BEHAVIOR: Returns true if n >= 0"
  [n]
  (and (number? n) (>= n 0)))

(defn validate-subscription-data
  "Pure function: Validates subscription data and returns errors if any.
  BEHAVIOR 1: If not a map, returns type error
  BEHAVIOR 2: If invalid tier, returns tier error
  BEHAVIOR 3: If price not a string, returns price-type error
  BEHAVIOR 4: If price can't be parsed, returns parse error
  BEHAVIOR 5: If price is negative, returns negative-price error
  BEHAVIOR 6: If all valid, returns nil (no errors)"
  [raw-data]
  (cond
    (not (map? raw-data))
    {:type :invalid-type}

    (not (valid-tier? (:tier raw-data)))
    {:type :invalid-tier :tier (:tier raw-data)}

    (not (string? (:price raw-data)))
    {:type :invalid-price}

    :else
    (let [price (parse-price (:price raw-data))]
      (cond
        (nil? price)
        {:type :parse-error :price (:price raw-data)}

        (not (non-negative? price))
        {:type :negative-price}

        :else
        nil))))

(defn transform-subscription-data
  "Pure function: Transforms validated subscription data.
  PRECONDITION: Data must be valid (call validate-subscription-data first)
  BEHAVIOR: Returns subscription map with calculated pricing"
  [raw-data]
  (let [tier (:tier raw-data)
        price (parse-price (:price raw-data))
        pricing (calculate-subscription-pricing tier price)]
    (merge {:tier tier} pricing)))

(defn validate-and-transform-subscription
  "Pure function: Validates and transforms subscription data.
  BEHAVIOR 1: If validation fails, throws ex-info with error
  BEHAVIOR 2: If validation succeeds, returns transformed data

  NOTE: This is the ONLY function in this module that throws.
  It's at the boundary and coordinates validation + transformation."
  [raw-data]
  (if-let [error (validate-subscription-data raw-data)]
    (throw (ex-info "Invalid subscription data" error))
    (transform-subscription-data raw-data)))

;; ==============================================================================
;; Side Effect Interface (Injectable Dependencies)
;; ==============================================================================
;; These functions define the INTERFACE for side effects.
;; In production, they would be implemented to talk to real databases, email services, etc.
;; In tests, they can be mocked to return controlled values.

(defn fetch-user
  "Side effect: Fetches user from database.
  This is an INTERFACE that should be mocked in tests."
  [db user-id]
  ;; In production, this would query the database
  (get db [:user user-id]))

(defn fetch-subscription
  "Side effect: Fetches subscription from database.
  This is an INTERFACE that should be mocked in tests."
  [db subscription-id]
  (get db [:subscription subscription-id]))

(defn fetch-balance
  "Side effect: Fetches balance from database.
  This is an INTERFACE that should be mocked in tests."
  [db user-id]
  (or (get db [:balance user-id]) 0))

(defn update-db!
  "Side effect: Updates database.
  This is an INTERFACE that should be mocked in tests."
  [db! key value]
  (db! key value))

(defn send-email!
  "Side effect: Sends an email.
  This is an INTERFACE that should be mocked in tests."
  [email-service! to subject body]
  (email-service! {:to to :subject subject :body body}))

(defn get-current-time
  "Side effect: Gets current time.
  This is an INTERFACE that should be mocked in tests."
  [clock]
  (clock))

;; ==============================================================================
;; Top-Level Orchestration (Side Effects at the Edges)
;; ==============================================================================

(defn gather-billing-data
  "Gathers all data needed for billing from various sources.
  This function does side effects (database queries) but is still
  relatively easy to test by mocking the data sources."
  [user-id db clock]
  (when-let [user (fetch-user db user-id)]
    (let [subscription (fetch-subscription db (:subscription-id user))
          balance (fetch-balance db user-id)
          current-date (get-current-time clock)]
      {:user user
       :subscription subscription
       :balance balance
       :current-date current-date})))

(defn execute-billing-plan!
  "Executes a billing plan by performing side effects.
  BEHAVIOR: Applies database updates and sends email"
  [plan db! email-service!]
  (doseq [[key user-id value] (:db-updates plan)]
    (update-db! db! [key user-id] value))
  (let [email (:email plan)]
    (send-email! email-service! (:to email) (:subject email) (:body email))))

(defn process-billing!
  "Top-level function that orchestrates the billing process.
  This function COORDINATES between pure logic and side effects.

  BEHAVIOR 1: Gathers data (side effect)
  BEHAVIOR 2: Creates plan using pure function (no side effects)
  BEHAVIOR 3: Executes plan (side effects)
  BEHAVIOR 4: Returns result

  Note: By separating plan creation from execution, we can test the
  planning logic WITHOUT side effects."
  [user-id db db! email-service! clock]
  (when-let [data (gather-billing-data user-id db clock)]
    (when-let [plan (billing-action-plan data)]
      (execute-billing-plan! plan db! email-service!)
      (:billing-result plan))))

(defn execute-task!
  "Executes a specific task.
  This is where task types are mapped to implementations."
  [task-type db! email-service! clock]
  (case task-type
    :billing (do
               (process-billing! 123 db! email-service! clock)
               (process-billing! 456 db! email-service! clock))
    :cleanup (update-db! db! [:cleanup-run] (get-current-time clock))
    :report (send-email! email-service!
              "admin@example.com"
              "Daily Report"
              (str "Report for " (get-current-time clock)))
    nil))

(defn run-daily-tasks!
  "Top-level function for running daily tasks.
  BEHAVIOR: Creates task plan, then executes each task"
  [db! email-service! clock]
  (let [plan (daily-task-plan (get-current-time clock))]
    (doseq [task (:tasks plan)]
      (execute-task! task db! email-service! clock))))
