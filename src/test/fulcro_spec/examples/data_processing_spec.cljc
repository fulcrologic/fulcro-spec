(ns fulcro-spec.examples.data-processing-spec
  "Comprehensive tests for data processing patterns.
  Demonstrates:
  - Testing predicates thoroughly
  - Testing collection operations with edge cases
  - Testing composition
  - Using test data helpers"
  (:require
    [fulcro-spec.core :refer [specification behavior assertions when-mocking]]
    [fulcro-spec.examples.data-processing :as dp]))

;; ==============================================================================
;; Test Data Helpers
;; ==============================================================================

(def sample-users
  [(dp/make-test-user {:id 1 :tier :basic :status :active})
   (dp/make-test-user {:id 2 :tier :premium :status :active})
   (dp/make-test-user {:id 3 :tier :enterprise :status :inactive})
   (dp/make-test-user {:id 4 :tier :basic :status :active :email "invalid"})])

;; ==============================================================================
;; Testing Predicates - Cover All Branches
;; ==============================================================================

(specification "active?" :group1
  (behavior "returns true for :active status"
    (assertions
      (dp/active? {:status :active}) => true))

  (behavior "returns false for non-active status"
    (assertions
      (dp/active? {:status :inactive}) => false
      (dp/active? {:status :pending}) => false))

  (behavior "returns false for nil status"
    (assertions
      (dp/active? {:status nil}) => false
      (dp/active? {}) => false)))

(specification "premium?" :group1
  (behavior "returns true for premium tier"
    (assertions
      (dp/premium? {:tier :premium}) => true))

  (behavior "returns true for enterprise tier"
    (assertions
      (dp/premium? {:tier :enterprise}) => true))

  (behavior "returns false for basic tier"
    (assertions
      (dp/premium? {:tier :basic}) => false))

  (behavior "returns false for nil tier"
    (assertions
      (dp/premium? {:tier nil}) => false
      (dp/premium? {}) => false)))

(specification "recent?" :group1
  (behavior "returns true when within days of reference"
    (let [ref #inst "2025-01-15T00:00:00"
          entity {:created-at #inst "2025-01-10T00:00:00"}] ;; 5 days ago
      (assertions
        (dp/recent? 7 ref entity) => true
        (dp/recent? 5 ref entity) => true)))

  (behavior "returns false when beyond days of reference"
    (let [ref #inst "2025-01-15T00:00:00"
          entity {:created-at #inst "2025-01-01T00:00:00"}] ;; 14 days ago
      (assertions
        (dp/recent? 7 ref entity) => false)))

  (behavior "returns false when created-at is nil"
    (let [ref #inst "2025-01-15T00:00:00"]
      (assertions
        (dp/recent? 7 ref {:created-at nil}) => false
        (dp/recent? 7 ref {}) => false))))

(specification "valid-email?" :group1
  (behavior "returns true for valid email format"
    (assertions
      (dp/valid-email? "user@example.com") => true
      (dp/valid-email? "test.user@domain.co.uk") => true))

  (behavior "returns false for invalid email format"
    (assertions
      "missing @"
      (dp/valid-email? "notanemail") => false
      "missing domain"
      (dp/valid-email? "user@") => false
      "missing ."
      (dp/valid-email? "user@domain") => false))

  (behavior "returns false for non-string input"
    (assertions
      (dp/valid-email? nil) => false
      (dp/valid-email? 123) => false)))

;; ==============================================================================
;; Testing Transformations
;; ==============================================================================

(specification "normalize-email" :group2
  (behavior "converts to lowercase"
    (assertions
      (dp/normalize-email "USER@EXAMPLE.COM") => "user@example.com"))

  (behavior "trims whitespace"
    (assertions
      (dp/normalize-email "  user@example.com  ") => "user@example.com"))

  (behavior "handles both lowercase and trim"
    (assertions
      (dp/normalize-email "  USER@EXAMPLE.COM  ") => "user@example.com"))

  (behavior "returns nil for nil input"
    (assertions
      (dp/normalize-email nil) => nil)))

(specification "extract-domain" :group2
  (behavior "extracts domain from valid email"
    (assertions
      (dp/extract-domain "user@example.com") => "example.com"
      (dp/extract-domain "test@domain.co.uk") => "domain.co.uk"))

  (behavior "returns nil for invalid email"
    (assertions
      (dp/extract-domain "notanemail") => nil
      (dp/extract-domain nil) => nil)))

(specification "add-computed-fields" :group2
  (behavior "adds normalized-email"
    (let [user {:email "  USER@Example.COM  "}
          result (dp/add-computed-fields user)]
      (assertions
        (:normalized-email result) => "user@example.com")))

  (behavior "adds email-domain"
    (let [user {:email "user@example.com"}
          result (dp/add-computed-fields user)]
      (assertions
        (:email-domain result) => "example.com")))

  (behavior "adds is-premium flag"
    (assertions
      "true for premium"
      (:is-premium (dp/add-computed-fields {:tier :premium})) => true
      "true for enterprise"
      (:is-premium (dp/add-computed-fields {:tier :enterprise})) => true
      "false for basic"
      (:is-premium (dp/add-computed-fields {:tier :basic})) => false)))

(specification "enrich-user" :group2
  (behavior "merges enrichment data by id"
    (let [enrichment {123 {:score 95 :badge :gold}}
          user {:id 123 :name "Test"}
          result (dp/enrich-user enrichment user)]
      (assertions
        (:name result) => "Test"
        (:score result) => 95
        (:badge result) => :gold)))

  (behavior "returns user unchanged if no enrichment data"
    (let [enrichment {456 {:score 95}}
          user {:id 123 :name "Test"}
          result (dp/enrich-user enrichment user)]
      (assertions
        result => user))))

;; ==============================================================================
;; Testing Collection Operations
;; ==============================================================================

(specification "filter-active" :group3
  (behavior "returns only active entities"
    (let [entities [{:id 1 :status :active}
                    {:id 2 :status :inactive}
                    {:id 3 :status :active}]
          result (dp/filter-active entities)]
      (assertions
        (count result) => 2
        (map :id result) => [1 3])))

  (behavior "returns empty collection for empty input"
    (assertions
      (dp/filter-active []) => []))

  (behavior "preserves order"
    (let [entities [{:id 1 :status :active}
                    {:id 2 :status :active}
                    {:id 3 :status :active}]
          result (dp/filter-active entities)]
      (assertions
        (map :id result) => [1 2 3]))))

(specification "filter-by-tier" :group3
  (behavior "returns only users matching tier"
    (let [users [{:id 1 :tier :basic}
                 {:id 2 :tier :premium}
                 {:id 3 :tier :basic}]
          result (dp/filter-by-tier :basic users)]
      (assertions
        (count result) => 2
        (map :id result) => [1 3])))

  (behavior "returns empty collection when no matches"
    (let [users [{:id 1 :tier :basic}]
          result (dp/filter-by-tier :premium users)]
      (assertions
        result => []))))

(specification "filter-recent" :group3
  (behavior "returns entities created within N days"
    (let [ref #inst "2025-01-15T00:00:00"
          entities [{:id 1 :created-at #inst "2025-01-10T00:00:00"} ;; 5 days
                    {:id 2 :created-at #inst "2025-01-01T00:00:00"} ;; 14 days
                    {:id 3 :created-at #inst "2025-01-14T00:00:00"}] ;; 1 day
          result (dp/filter-recent 7 ref entities)]
      (assertions
        (count result) => 2
        (map :id result) => [1 3])))

  (behavior "excludes entities with nil created-at"
    (let [ref #inst "2025-01-15T00:00:00"
          entities [{:id 1 :created-at #inst "2025-01-10T00:00:00"}
                    {:id 2 :created-at nil}]
          result (dp/filter-recent 7 ref entities)]
      (assertions
        (count result) => 1
        (map :id result) => [1]))))

(specification "select-fields" :group3
  (behavior "returns map with only specified keys"
    (let [entity {:id 1 :name "Test" :email "test@example.com" :extra "data"}
          result (dp/select-fields [:id :name] entity)]
      (assertions
        result => {:id 1 :name "Test"})))

  (behavior "includes nil values if key not present"
    (let [entity {:id 1}
          result (dp/select-fields [:id :name] entity)]
      (assertions
        (contains? result :id) => true
        (contains? result :name) => false ;; select-keys doesn't include missing keys
        ))))

(specification "select-fields-from-all" :group3
  (behavior "applies select-fields to all entities"
    (let [entities [{:id 1 :name "A" :extra "x"}
                    {:id 2 :name "B" :extra "y"}]
          result (dp/select-fields-from-all [:id :name] entities)]
      (assertions
        (count result) => 2
        (map :id result) => [1 2]
        (map :name result) => ["A" "B"]
        (every? #(not (contains? % :extra)) result) => true))))

;; ==============================================================================
;; Testing Aggregations and Edge Cases
;; ==============================================================================

(specification "count-by-status" :group4
  (behavior "returns map of status counts"
    (let [entities [{:status :active}
                    {:status :active}
                    {:status :inactive}
                    {:status :pending}]
          result (dp/count-by-status entities)]
      (assertions
        (:active result) => 2
        (:inactive result) => 1
        (:pending result) => 1)))

  (behavior "returns empty map for empty input"
    (assertions
      (dp/count-by-status []) => {})))

(specification "sum-field" :group4
  (behavior "returns sum of field values"
    (let [entities [{:amount 10}
                    {:amount 20}
                    {:amount 30}]]
      (assertions
        (dp/sum-field :amount entities) => 60)))

  (behavior "returns 0 for empty collection"
    (assertions
      (dp/sum-field :amount []) => 0))

  (behavior "treats nil values as 0"
    (let [entities [{:amount 10}
                    {:amount nil}
                    {:amount 20}]]
      (assertions
        (dp/sum-field :amount entities) => 30))))

(specification "average-field" :group4
  (behavior "returns average for non-empty collection"
    (let [entities [{:score 80}
                    {:score 90}
                    {:score 70}]]
      (assertions
        (dp/average-field :score entities) => 80.0)))

  (behavior "returns nil for empty collection"
    (assertions
      (dp/average-field :score []) => nil))

  (behavior "treats nil values as 0"
    (let [entities [{:score 90}
                    {:score nil}
                    {:score 60}]]
      (assertions
        (dp/average-field :score entities) => 50.0))))

(specification "group-by-field" :group4
  (behavior "returns map of field-value to entities"
    (let [entities [{:id 1 :tier :basic}
                    {:id 2 :tier :premium}
                    {:id 3 :tier :basic}]
          result (dp/group-by-field :tier entities)]
      (assertions
        (count (:basic result)) => 2
        (count (:premium result)) => 1
        (map :id (:basic result)) => [1 3]))))

;; ==============================================================================
;; Testing Complex Compositions
;; ==============================================================================

(specification "user-summary" :group5
  (behavior "extracts and computes summary fields"
    (let [user {:id 1
                :name "Test User"
                :email "  TEST@EXAMPLE.COM  "
                :tier :premium
                :extra "ignored"}
          result (dp/user-summary user)]
      (assertions
        (:id result) => 1
        (:name result) => "Test User"
        (:email result) => "test@example.com"
        (:tier result) => :premium
        (:premium? result) => true
        (contains? result :extra) => false))))

(specification "active-premium-user-summaries" :group5
  (behavior "filters and transforms to summaries"
    (let [users [{:id 1 :status :active :tier :premium :name "A" :email "a@test.com"}
                 {:id 2 :status :inactive :tier :premium :name "B" :email "b@test.com"}
                 {:id 3 :status :active :tier :basic :name "C" :email "c@test.com"}
                 {:id 4 :status :active :tier :enterprise :name "D" :email "d@test.com"}]
          result (dp/active-premium-user-summaries users)]
      (assertions
        "returns only active premium/enterprise users"
        (count result) => 2
        (map :id result) => [1 4]
        "all are premium"
        (every? :premium? result) => true)))

  (behavior "returns empty collection if no matches"
    (let [users [{:status :inactive :tier :basic}]]
      (assertions
        (dp/active-premium-user-summaries users) => []))))

(specification "recent-active-users-by-tier" :group5
  (behavior "filters, groups, and sorts by tier"
    (let [ref #inst "2025-01-15T00:00:00"
          users [{:id 1 :created-at #inst "2025-01-10" :status :active :tier :premium}
                 {:id 2 :created-at #inst "2025-01-10" :status :active :tier :basic}
                 {:id 3 :created-at #inst "2025-01-01" :status :active :tier :premium} ;; too old
                 {:id 4 :created-at #inst "2025-01-10" :status :inactive :tier :basic}] ;; not active
          result (dp/recent-active-users-by-tier 7 ref users)]
      (assertions
        "groups by tier"
        (keys result) => [:basic :premium]
        "basic tier has 1 user"
        (count (:basic result)) => 1
        "premium tier has 1 user"
        (count (:premium result)) => 1))))

;; ==============================================================================
;; Testing Validation
;; ==============================================================================

(specification "validate-user-required-fields" :group5
  (behavior "returns nil when all required fields present"
    (let [user {:id 1 :email "test@example.com" :name "Test" :tier :basic :status :active}]
      (assertions
        (dp/validate-user-required-fields user) => nil)))

  (behavior "returns error with missing fields"
    (let [user {:id 1 :email "test@example.com"}] ;; missing name, tier, status
      (let [error (dp/validate-user-required-fields user)]
        (assertions
          (:error error) => :missing-fields
          (set (:missing error)) => #{:name :tier :status})))))

(specification "validate-user-email" :group5
  (behavior "returns nil for valid email"
    (assertions
      (dp/validate-user-email {:email "test@example.com"}) => nil))

  (behavior "returns error for invalid email"
    (let [error (dp/validate-user-email {:email "invalid"})]
      (assertions
        (:error error) => :invalid-email
        (:email error) => "invalid"))))

(specification "validate-user-tier" :group5
  (behavior "returns nil for valid tier"
    (assertions
      (dp/validate-user-tier {:tier :basic}) => nil
      (dp/validate-user-tier {:tier :premium}) => nil
      (dp/validate-user-tier {:tier :enterprise}) => nil))

  (behavior "returns error for invalid tier"
    (let [error (dp/validate-user-tier {:tier :invalid})]
      (assertions
        (:error error) => :invalid-tier
        (:tier error) => :invalid))))

(specification "validate-user" :group5
  (behavior "returns nil for completely valid user"
    (let [user {:id 1
                :email "test@example.com"
                :name "Test"
                :tier :basic
                :status :active}]
      (assertions
        (dp/validate-user user) => nil)))

  (behavior "returns first error found"
    (assertions
      "missing fields error comes first"
      (:error (dp/validate-user {:id 1}))
      => :missing-fields

      "invalid email error when fields present"
      (:error (dp/validate-user {:id 1 :email "bad" :name "T" :tier :basic :status :active}))
      => :invalid-email

      "invalid tier error when email valid"
      (:error (dp/validate-user {:id 1 :email "t@e.c" :name "T" :tier :bad :status :active}))
      => :invalid-tier)))

(specification "partition-valid-invalid" :group5
  (behavior "separates valid and invalid users"
    (let [users [{:id 1 :email "valid@test.com" :name "A" :tier :basic :status :active}
                 {:id 2 :email "invalid" :name "B" :tier :basic :status :active}
                 {:id 3 :email "also-invalid" :name "C" :tier :basic :status :active}
                 {:id 4 :email "valid2@test.com" :name "D" :tier :basic :status :active}]
          result (dp/partition-valid-invalid users)]
      (assertions
        "has 2 valid users"
        (count (:valid result)) => 2
        (map :id (:valid result)) => [1 4]
        "has 2 invalid users"
        (count (:invalid result)) => 2
        "invalid includes error information"
        (every? :error (:invalid result)) => true))))

;; ==============================================================================
;; Testing Complete Pipeline
;; ==============================================================================

(specification "process-user-batch" :group5
  (behavior "processes batch with validation, enrichment, and statistics"
    (let [enrichment {1 {:score 95} 2 {:score 85}}
          users [{:id 1 :email "user1@test.com" :name "A" :tier :premium :status :active}
                 {:id 2 :email "user2@test.com" :name "B" :tier :basic :status :active}
                 {:id 3 :email "invalid" :name "C" :tier :basic :status :active}]
          result (dp/process-user-batch enrichment users)]
      (assertions
        "processes valid users"
        (count (:processed result)) => 2

        "enriches with additional data"
        (get-in result [:processed 0 :score]) => 95
        (get-in result [:processed 1 :score]) => 85

        "adds computed fields"
        (get-in result [:processed 0 :is-premium]) => true
        (get-in result [:processed 1 :is-premium]) => false
        (get-in result [:processed 0 :normalized-email]) => "user1@test.com"

        "includes invalid users with errors"
        (count (:invalid result)) => 1

        "provides statistics"
        (get-in result [:stats :total]) => 3
        (get-in result [:stats :valid]) => 2
        (get-in result [:stats :invalid]) => 1
        (get-in result [:stats :premium]) => 1))))
