(ns fulcro-spec.examples.data-processing
  "Examples demonstrating how to structure and test data processing functions.
  Shows patterns for:
  - Collection transformations
  - Filtering and predicates
  - Edge case handling
  - Composing transformations
  - Testing all code paths"
  (:require
    [clojure.string :as str]))

;; ==============================================================================
;; Predicates (Filters) - Each is a Testable Behavior
;; ==============================================================================

(defn active?
  "Pure predicate: Checks if an entity is active.
  BEHAVIOR 1: Returns true if :status is :active
  BEHAVIOR 2: Returns false otherwise"
  [entity]
  (= :active (:status entity)))

(defn premium?
  "Pure predicate: Checks if user has premium tier.
  BEHAVIOR 1: Returns true if :tier is premium or enterprise
  BEHAVIOR 2: Returns false otherwise"
  [user]
  (boolean (#{:premium :enterprise} (:tier user))))

(defn recent?
  "Pure predicate: Checks if created-at is within days of reference date.
  BEHAVIOR 1: Returns true if within days
  BEHAVIOR 2: Returns false otherwise
  BEHAVIOR 3: Returns false if created-at is nil"
  [days reference-date entity]
  (boolean
    (when-let [created (:created-at entity)]
      (let [diff-ms (- (.getTime reference-date) (.getTime created))
            diff-days (/ diff-ms (* 1000.0 60 60 24))]
        (<= diff-days days)))))

(defn valid-email?
  "Pure predicate: Basic email validation.
  BEHAVIOR 1: Returns true if string contains @ and .
  BEHAVIOR 2: Returns false otherwise"
  [email]
  (and (string? email)
       (str/includes? email "@")
       (str/includes? email ".")))

;; ==============================================================================
;; Transformations - Pure Functions
;; ==============================================================================

(defn normalize-email
  "Pure function: Normalizes email to lowercase and trimmed.
  BEHAVIOR 1: Converts to lowercase
  BEHAVIOR 2: Trims whitespace
  BEHAVIOR 3: Returns nil for nil input"
  [email]
  (when email
    (-> email str/trim str/lower-case)))

(defn extract-domain
  "Pure function: Extracts domain from email.
  BEHAVIOR 1: Returns part after @ for valid email
  BEHAVIOR 2: Returns nil for invalid email"
  [email]
  (when (valid-email? email)
    (second (str/split email #"@"))))

(defn add-computed-fields
  "Pure function: Adds computed fields to user map.
  BEHAVIOR 1: Adds :normalized-email
  BEHAVIOR 2: Adds :email-domain
  BEHAVIOR 3: Adds :is-premium flag"
  [user]
  (let [normalized (normalize-email (:email user))]
    (assoc user
      :normalized-email normalized
      :email-domain (extract-domain normalized)
      :is-premium (premium? user))))

(defn enrich-user
  "Pure function: Enriches user with additional data.
  BEHAVIOR: Merges user with enrichment data by :id"
  [enrichment-data user]
  (if-let [extra (get enrichment-data (:id user))]
    (merge user extra)
    user))

;; ==============================================================================
;; Collection Operations - Composing Predicates and Transformations
;; ==============================================================================

(defn filter-active
  "Pure function: Filters collection to active entities.
  BEHAVIOR 1: Returns only entities where (active? entity) is true
  BEHAVIOR 2: Returns empty collection if input is empty
  BEHAVIOR 3: Preserves order"
  [entities]
  (filter active? entities))

(defn filter-by-tier
  "Pure function: Filters users by tier.
  BEHAVIOR: Returns only users matching the specified tier"
  [tier users]
  (filter #(= tier (:tier %)) users))

(defn filter-recent
  "Pure function: Filters entities created within N days of reference date.
  BEHAVIOR: Returns only entities where (recent? days ref entity) is true"
  [days reference-date entities]
  (filter (partial recent? days reference-date) entities))

(defn select-fields
  "Pure function: Selects only specified fields from maps.
  BEHAVIOR 1: Returns map with only the specified keys
  BEHAVIOR 2: Includes nil values if key not present"
  [fields entity]
  (select-keys entity fields))

(defn select-fields-from-all
  "Pure function: Applies select-fields to all entities in collection.
  BEHAVIOR: Maps select-fields over collection"
  [fields entities]
  (map (partial select-fields fields) entities))

;; ==============================================================================
;; Aggregations - Pure Functions with Edge Cases
;; ==============================================================================

(defn count-by-status
  "Pure function: Counts entities grouped by status.
  BEHAVIOR 1: Returns map of {status count}
  BEHAVIOR 2: Returns empty map for empty input
  BEHAVIOR 3: Includes all statuses present in input"
  [entities]
  (frequencies (map :status entities)))

(defn sum-field
  "Pure function: Sums a numeric field across entities.
  BEHAVIOR 1: Returns sum of field values
  BEHAVIOR 2: Returns 0 for empty collection
  BEHAVIOR 3: Treats nil values as 0"
  [field entities]
  (reduce + 0 (map #(or (get % field) 0) entities)))

(defn average-field
  "Pure function: Calculates average of a field.
  BEHAVIOR 1: Returns average for non-empty collection
  BEHAVIOR 2: Returns nil for empty collection
  BEHAVIOR 3: Treats nil values as 0"
  [field entities]
  (when (seq entities)
    (/ (sum-field field entities)
       (double (count entities)))))

(defn group-by-field
  "Pure function: Groups entities by field value.
  BEHAVIOR: Returns map of {field-value [entities]}"
  [field entities]
  (group-by #(get % field) entities))

;; ==============================================================================
;; Complex Transformations - Composing Multiple Operations
;; ==============================================================================

(defn user-summary
  "Pure function: Creates summary from user data.
  BEHAVIOR 1: Extracts key fields
  BEHAVIOR 2: Adds computed premium flag
  BEHAVIOR 3: Includes normalized email"
  [user]
  {:id (:id user)
   :name (:name user)
   :email (normalize-email (:email user))
   :tier (:tier user)
   :premium? (premium? user)})

(defn active-premium-user-summaries
  "Pure function: Gets summaries of active premium users.
  This demonstrates composition of multiple operations.
  BEHAVIOR 1: Filters to active users
  BEHAVIOR 2: Filters to premium users
  BEHAVIOR 3: Transforms to summaries
  BEHAVIOR 4: Returns empty collection if no matches"
  [users]
  (->> users
    (filter active?)
    (filter premium?)
    (map user-summary)))

(defn recent-active-users-by-tier
  "Pure function: Groups recent active users by tier.
  Complex composition demonstrating:
  BEHAVIOR 1: Filters to recent (within days)
  BEHAVIOR 2: Filters to active
  BEHAVIOR 3: Groups by tier
  BEHAVIOR 4: Sorts tiers in result"
  [days reference-date users]
  (->> users
    (filter (partial recent? days reference-date))
    (filter active?)
    (group-by :tier)
    (into (sorted-map))))

;; ==============================================================================
;; Validation and Error Handling
;; ==============================================================================

(defn validate-user-required-fields
  "Pure function: Validates user has required fields.
  BEHAVIOR 1: Returns nil (no error) if all required fields present
  BEHAVIOR 2: Returns error map with missing fields if any missing"
  [user]
  (let [required [:id :email :name :tier :status]
        missing (filter #(nil? (get user %)) required)]
    (when (seq missing)
      {:error :missing-fields
       :missing missing})))

(defn validate-user-email
  "Pure function: Validates user email format.
  BEHAVIOR 1: Returns nil if email is valid
  BEHAVIOR 2: Returns error map if email invalid"
  [user]
  (when-not (valid-email? (:email user))
    {:error :invalid-email
     :email (:email user)}))

(defn validate-user-tier
  "Pure function: Validates user tier is recognized.
  BEHAVIOR 1: Returns nil if tier is valid
  BEHAVIOR 2: Returns error map if tier invalid"
  [user]
  (when-not (#{:basic :premium :enterprise} (:tier user))
    {:error :invalid-tier
     :tier (:tier user)}))

(defn validate-user
  "Pure function: Runs all validations on a user.
  BEHAVIOR 1: Returns nil if all validations pass
  BEHAVIOR 2: Returns first error found
  BEHAVIOR 3: Checks in order: required fields, email, tier"
  [user]
  (or (validate-user-required-fields user)
      (validate-user-email user)
      (validate-user-tier user)))

(defn partition-valid-invalid
  "Pure function: Separates valid and invalid users.
  BEHAVIOR: Returns map with :valid and :invalid keys
  Each invalid entry includes the validation error"
  [users]
  (let [with-validation (map (fn [user]
                               (if-let [error (validate-user user)]
                                 {:user user :error error :valid? false}
                                 {:user user :valid? true}))
                          users)
        grouped (group-by :valid? with-validation)]
    {:valid (map :user (get grouped true []))
     :invalid (map #(select-keys % [:user :error])
                (get grouped false []))}))

;; ==============================================================================
;; Batch Processing - Pure Pipeline
;; ==============================================================================

(defn process-user-batch
  "Pure function: Processes a batch of users through a pipeline.
  Demonstrates a complete data processing pipeline:
  BEHAVIOR 1: Validates all users, separating valid/invalid
  BEHAVIOR 2: Enriches valid users with additional data
  BEHAVIOR 3: Adds computed fields
  BEHAVIOR 4: Returns processed data with statistics"
  [enrichment-data users]
  (let [{:keys [valid invalid]} (partition-valid-invalid users)
        enriched (mapv (partial enrich-user enrichment-data) valid)
        processed (mapv add-computed-fields enriched)]
    {:processed processed
     :invalid invalid
     :stats {:total (count users)
             :valid (count valid)
             :invalid (count invalid)
             :premium (count (filter :is-premium processed))}}))

;; ==============================================================================
;; Testing Utility - Demonstrates Test Data Generation
;; ==============================================================================

(defn make-test-user
  "Test helper: Creates a user with defaults that can be overridden.
  This is useful for test data generation."
  [overrides]
  (merge
    {:id 1
     :name "Test User"
     :email "test@example.com"
     :tier :basic
     :status :active
     :created-at #inst "2025-01-01T00:00:00"}
    overrides))
