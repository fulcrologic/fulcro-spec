(ns fulcro-spec.examples.billing-bad
  "Example of POORLY structured code that is hard to test.
  This demonstrates common anti-patterns:
  - Mixed levels of abstraction
  - Side effects intermingled with business logic
  - Large functions with multiple responsibilities
  - Hard to mock dependencies
  - Difficult to test all code paths"
  (:require
    [clojure.string :as str]))

;; Simulated database and external services (side effects)
(defonce ^:private db-state (atom {}))
(defonce ^:private sent-emails (atom []))
(defonce ^:private current-time (atom #inst "2025-01-15T00:00:00"))

(defn- db-query [query]
  "Simulates a database query - a side effect"
  (get @db-state query))

(defn- db-update! [k v]
  "Simulates a database update - a side effect"
  (swap! db-state assoc k v))

(defn- send-email! [to subject body]
  "Simulates sending an email - a side effect"
  (swap! sent-emails conj {:to to :subject subject :body body}))

(defn- now []
  "Simulates getting current time - a side effect"
  @current-time)

;; BAD: This function mixes multiple levels of abstraction and has side effects everywhere
(defn process-billing!
  "ANTI-PATTERN: This function is very difficult to test because:
  1. It mixes high-level logic (billing process) with low-level details (string formatting, date math)
  2. It has side effects (DB queries, updates, email) mixed with business logic
  3. It's difficult to test all branches without actually hitting the database
  4. Cannot easily test error conditions
  5. Hard to verify email content without side effects"
  [user-id]
  (let [user (db-query [:user user-id])]
    (when user
      (let [subscription (db-query [:subscription (:subscription-id user)])
            amount (:monthly-price subscription)
            last-billed (:last-billed user)
            today (now)
            days-since-billing (when last-billed
                                 (/ (- (.getTime today)
                                      (.getTime last-billed))
                                   (* 1000 60 60 24)))]
        ;; Mixed abstraction: date math, business rules, and side effects all together
        (when (or (nil? last-billed)
                (>= days-since-billing 30))
          ;; Check balance - more side effects mixed with logic
          (let [balance (or (db-query [:balance user-id]) 0)]
            (if (>= balance amount)
              (do
                ;; Success case: deduct, update, send email
                (db-update! [:balance user-id] (- balance amount))
                (db-update! [:last-billed user-id] today)
                ;; String formatting mixed with high-level logic
                (send-email! (:email user)
                  "Billing Successful"
                  (str "We have charged $" amount " to your account. "
                    "Your remaining balance is $" (- balance amount) ". "
                    "Thank you for your subscription!"))
                {:status :success
                 :amount amount
                 :new-balance (- balance amount)})
              ;; Failure case: send different email
              (do
                (send-email! (:email user)
                  "Billing Failed"
                  (str "We were unable to charge $" amount " to your account. "
                    "Your current balance is $" balance ". "
                    "Please add funds to avoid service interruption."))
                {:status :insufficient-funds
                 :required amount
                 :available balance}))))))))

;; BAD: Another example - scheduling logic mixed with execution
(defn run-daily-tasks!
  "ANTI-PATTERN: Hard to test because:
  1. Tightly couples scheduling logic with task execution
  2. Cannot test the scheduling logic without side effects
  3. Cannot easily verify what gets called when"
  []
  (let [today (now)
        day-of-week (.getDay today)] ;; 0 = Sunday, 1 = Monday, etc.
    ;; Business logic mixed with side effects
    (when (= day-of-week 2) ;; Tuesday
      (process-billing! 123)
      (process-billing! 456))
    (when (= day-of-week 0) ;; Sunday
      ;; Imagine some cleanup task
      (db-update! [:cleanup-run] today))
    (when (#{1 3 5} day-of-week) ;; Monday, Wednesday, Friday
      ;; Imagine some report generation
      (send-email! "admin@example.com"
        "Daily Report"
        (str "Report for " today)))))

;; BAD: Complex validation mixed with data transformation
(defn validate-and-transform-subscription
  "ANTI-PATTERN: Mixes validation, transformation, and error handling
  in a way that makes it hard to test individual concerns"
  [raw-data]
  (when-not (map? raw-data)
    (throw (ex-info "Invalid input" {:type :invalid-type})))
  (let [tier (get raw-data :tier)
        price-str (get raw-data :price)]
    (when-not (contains? #{"basic" "premium" "enterprise"} tier)
      (throw (ex-info "Invalid tier" {:type :invalid-tier :tier tier})))
    (when-not (string? price-str)
      (throw (ex-info "Price must be string" {:type :invalid-price})))
    (let [price (try
                  #?(:clj (Double/parseDouble price-str)
                     :cljs (js/parseFloat price-str))
                  (catch #?(:clj Exception :cljs js/Error) _
                    (throw (ex-info "Cannot parse price" {:type :parse-error :price price-str}))))]
      (when (< price 0)
        (throw (ex-info "Price cannot be negative" {:type :negative-price})))
      ;; Business logic mixed with transformation
      (let [adjusted-price (cond
                            (= tier "basic") (* price 1.0)
                            (= tier "premium") (* price 0.9) ;; 10% discount
                            (= tier "enterprise") (* price 0.8))] ;; 20% discount
        {:tier tier
         :monthly-price adjusted-price
         :annual-price (* adjusted-price 12 0.95)})))) ;; 5% discount for annual
