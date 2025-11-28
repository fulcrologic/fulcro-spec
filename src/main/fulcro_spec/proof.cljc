(ns fulcro-spec.proof
  "Tools for verifying transitive test coverage proofs.

   This namespace provides functions to verify that a function and all its
   transitive dependencies have declared test coverage. This enables building
   a provable chain of tests from low-level functions up to application logic.

   Additionally, it supports staleness detection: when a function's source code
   changes after a test was sealed, the coverage is marked as stale until the
   developer reviews and re-seals the test.

   Configuration:
   Configuration is automatically loaded from `.fulcro-spec.edn` in the project root
   when first needed. You can also use `configure!` to set options programmatically:

   - :scope-ns-prefixes - set of namespace prefix strings to include (e.g. #{\"myapp\"})
   - :enforce? - when true, when-mocking!! and provided!! will throw on missing coverage

   Example .fulcro-spec.edn:
     {:scope-ns-prefixes #{\"myapp\" \"myapp.lib\"}
      :enforce? false}

   Or programmatically:
     (proof/configure! {:scope-ns-prefixes #{\"myapp\"}})"
  (:require
    [fulcro-spec.coverage :as cov]
    #?(:clj [fulcro-spec.signature :as sig])
    #?(:clj [com.fulcrologic.guardrails.impl.externs :as gr.externs])
    #?(:clj [clojure.set :as set])
    #?(:clj [clojure.java.io :as io])
    #?(:clj [clojure.edn :as edn])))

;; =============================================================================
;; Global Configuration
;; =============================================================================

;; Global configuration atom for proof settings.
;; Starts as nil; lazily loaded from .fulcro-spec.edn when first accessed.
;; Use `configure!` to set explicitly, `get-config` to read.
(defonce ^:private config* (atom nil))

;; Track if we've already warned about missing config
(defonce ^:private warned-missing-config?* (atom false))

(def ^:private default-config
  {:scope-ns-prefixes #{}
   :enforce?          false})

#?(:clj
   (defn- load-config-file
     "Attempts to load .fulcro-spec.edn from the current directory.
      Returns nil if file doesn't exist or can't be read.
      Logs warning once if file is missing."
     []
     (try
       (let [f (io/file ".fulcro-spec.edn")]
         (if (.exists f)
           (edn/read-string (slurp f))
           (do
             (when-not @warned-missing-config?*
               (reset! warned-missing-config?* true)
               (binding [*out* *err*]
                 (println "WARNING: .fulcro-spec.edn not found. Coverage features disabled.")
                 (println "         Create .fulcro-spec.edn with {:scope-ns-prefixes #{\"your.ns.prefix\"}}")))
             nil)))
       (catch Exception e
         (binding [*out* *err*]
           (println "WARNING: Error reading .fulcro-spec.edn:" (.getMessage e)))
         nil))))

(defn get-config
  "Returns the current proof configuration.

   If not explicitly configured via `configure!`, attempts to load from
   `.fulcro-spec.edn` in the current directory. Falls back to defaults
   if no config file exists (with a warning)."
  []
  (or @config*
    #?(:clj
       (let [file-config (load-config-file)]
         (when file-config
           (reset! config* (merge default-config file-config)))
         (or @config* default-config))
       :cljs
       default-config)))

(defn configure!
  "Configure global proof settings. Options:
   - :scope-ns-prefixes - Set of namespace prefix strings that define which
                          namespaces are 'in scope' for coverage checking.
                          E.g., #{\"myapp\"} includes myapp.core, myapp.db, etc.
   - :enforce? - When true, when-mocking!! and provided!! will throw exceptions
                 if mocked functions lack transitive coverage. When false, they
                 behave like when-mocking!/provided!. Default: false.

   This overrides any configuration from .fulcro-spec.edn."
  [opts]
  (reset! config* (merge default-config (get-config) opts)))

(defn- effective-scope
  "Returns the scope-ns-prefixes to use, preferring explicit opts over global config."
  [opts]
  (or (:scope-ns-prefixes opts)
    (:scope-ns-prefixes (get-config))))

(defn- enforcement-enabled?
  "Returns whether proof enforcement is enabled."
  []
  (:enforce? (get-config)))

;; =============================================================================
;; Signature and Freshness
;; =============================================================================

#?(:clj
   (defn signature
     "Returns the current signature for a function.

      Automatically returns the appropriate format based on call graph:
      - LEAF functions (no in-scope callees): \"xxxxxx\" (6-char hash of source)
      - NON-LEAF functions: \"xxxxxx,yyyyyy\" (self + callee hash)

      Use this to get the signature when sealing a test.

      Example:
        (signature 'myapp.orders/create-order)
        ;; => \"a1b2c3\" (leaf) or \"a1b2c3,d4e5f6\" (non-leaf)"
     ([fn-sym] (signature fn-sym (:scope-ns-prefixes (get-config))))
     ([fn-sym scope-ns-prefixes]
      (sig/signature fn-sym scope-ns-prefixes))))

#?(:clj
   (defn sealed?
     "Returns true if the function has a sealed signature recorded.

      A function is sealed when a test declares coverage with a signature:
        (specification {:covers {`my-fn \"abc123\"}} ...)

      Unsealed functions have coverage declared but no signature for staleness tracking."
     [fn-sym]
     (boolean (cov/sealed-signature fn-sym))))

#?(:clj
   (defn fresh?
     "Returns true if the function has a sealed signature that matches its current source.

      A function is fresh when:
      - It HAS a sealed signature (from a :covers declaration with signature), AND
      - The sealed signature matches the current computed signature

      Returns false if:
      - The function is not sealed (no signature in :covers), OR
      - The function is stale (signature doesn't match)

      Use `sealed?` to check if a function has any signature tracking.
      Use `stale?` to check if a sealed function has changed."
     ([fn-sym] (fresh? fn-sym (:scope-ns-prefixes (get-config))))
     ([fn-sym scope-ns-prefixes]
      (let [sealed (cov/sealed-signature fn-sym)]
        (and (some? sealed)
          (seq scope-ns-prefixes)
          (= sealed (sig/signature fn-sym scope-ns-prefixes)))))))

#?(:clj
   (defn stale?
     "Returns true if the function has a sealed signature that differs from current.

      A stale function means its implementation has changed since the covering
      test was sealed. The test needs review and re-sealing.

      Returns false if:
      - The function is not sealed (no signature to compare), OR
      - The function is fresh (signatures match)"
     ([fn-sym] (stale? fn-sym (:scope-ns-prefixes (get-config))))
     ([fn-sym scope-ns-prefixes]
      (let [sealed (cov/sealed-signature fn-sym)]
        (and (some? sealed)
          (seq scope-ns-prefixes)
          (not= sealed (sig/signature fn-sym scope-ns-prefixes)))))))

;; =============================================================================
;; Core Verification Functions
;; =============================================================================

(defn verify-transitive-coverage
  "Verify that fn-sym and all its transitive dependencies have test coverage.

   Returns a report map:
   - :function - the function checked
   - :scope-ns-prefixes - the namespace scope used
   - :transitive-deps - all guardrailed functions in the call graph
   - :covered - set of functions with declared coverage
   - :uncovered - set of functions without declared coverage
   - :stale - set of functions with stale coverage (signature mismatch)
   - :proof-complete? - true if all deps are covered AND none are stale

   Options (optional if global config is set):
   - :scope-ns-prefixes - set of namespace prefix strings to include"
  ([fn-sym] (verify-transitive-coverage fn-sym {}))
  ([fn-sym opts]
   #?(:clj
      (let [scope (effective-scope opts)]
        (if (empty? scope)
          {:function          fn-sym
           :scope-ns-prefixes scope
           :transitive-deps   #{}
           :covered           #{}
           :uncovered         #{}
           :stale             #{}
           :proof-complete?   false
           :error             :no-scope-configured
           :note              "No scope configured - use configure! or pass :scope-ns-prefixes"}
          (let [all-deps  (gr.externs/transitive-calls fn-sym scope)
                covered   (into #{} (filter cov/covered? all-deps))
                uncovered (set/difference all-deps covered)
                stale-fns (into #{} (filter #(stale? % scope) covered))]
            {:function          fn-sym
             :scope-ns-prefixes scope
             :transitive-deps   all-deps
             :covered           covered
             :uncovered         uncovered
             :stale             stale-fns
             :proof-complete?   (and (empty? uncovered) (empty? stale-fns))})))
      :cljs
      {:function          fn-sym
       :scope-ns-prefixes (effective-scope opts)
       :transitive-deps   #{}
       :covered           #{}
       :uncovered         #{}
       :stale             #{}
       :proof-complete?   false
       :error             :cljs-not-supported
       :note              "Call graph analysis not available in ClojureScript"})))

(defn fully-tested?
  "Returns true if the function and all its transitive dependencies have
   declared test coverage within the configured scope AND none are stale.

   This is the simple query function for checking coverage status.
   Configure scope first with `configure!` or pass opts.

   Examples:
     (fully-tested? 'myapp.orders/create-order)
     (fully-tested? 'myapp.orders/create-order {:scope-ns-prefixes #{\"myapp\"}})"
  ([fn-sym] (fully-tested? fn-sym {}))
  ([fn-sym opts]
   (:proof-complete? (verify-transitive-coverage fn-sym opts))))

(defn why-not-tested?
  "Returns a map of issues if fn-sym is not fully tested, or nil if fully tested.

   The returned map contains:
   - :uncovered - set of functions without declared coverage
   - :stale - set of functions with stale coverage (signature mismatch)

   Examples:
     (why-not-tested? 'myapp.orders/create-order)
     ;; => {:uncovered #{myapp.db/save!} :stale #{myapp.validation/check}}"
  ([fn-sym] (why-not-tested? fn-sym {}))
  ([fn-sym opts]
   (let [{:keys [proof-complete? uncovered stale]} (verify-transitive-coverage fn-sym opts)]
     (when-not proof-complete?
       (cond-> {}
         (seq uncovered) (assoc :uncovered uncovered)
         (seq stale) (assoc :stale stale))))))

;; =============================================================================
;; Assertion and Enforcement
;; =============================================================================

(defn assert-transitive-coverage!
  "Assert that fn-sym has complete transitive coverage (no uncovered or stale deps).
   Throws an exception if any in-scope dependency lacks coverage or is stale.
   Used by when-mocking!! at test time.

   When called without opts, uses global config.
   Only throws if :enforce? is true in config (or if opts explicitly passed)."
  ([fn-sym] (assert-transitive-coverage! fn-sym {}))
  ([fn-sym opts]
   #?(:clj
      (let [scope           (effective-scope opts)
            ;; If opts were explicitly passed with scope, always enforce
            ;; Otherwise respect the global :enforce? flag
            should-enforce? (or (seq (:scope-ns-prefixes opts))
                              (enforcement-enabled?))]
        (when (and should-enforce? (seq scope))
          (let [{:keys [uncovered stale proof-complete?]} (verify-transitive-coverage fn-sym opts)]
            (when-not proof-complete?
              (throw (ex-info (str "Incomplete transitive coverage for " fn-sym
                                ". Uncovered: " (pr-str uncovered)
                                ". Stale: " (pr-str stale))
                       {:function          fn-sym
                        :uncovered         uncovered
                        :stale             stale
                        :scope-ns-prefixes scope}))))))
      :cljs nil)))

;; =============================================================================
;; Staleness Queries
;; =============================================================================

#?(:clj
   (defn stale-coverage
     "Returns a map of all functions with stale coverage in the given scope.

      For each stale function, returns:
      - :sealed-sig - the signature recorded when test was sealed
      - :current-sig - the current signature of the function
      - :tested-by - the test(s) covering this function

      Uses global config if no argument provided."
     ([] (stale-coverage (:scope-ns-prefixes (get-config))))
     ([scope-ns-prefixes]
      (if (empty? scope-ns-prefixes)
        {}
        (let [all-fns     (gr.externs/all-in-scope-functions scope-ns-prefixes)
              covered-fns (filter cov/covered? all-fns)]
          (into {}
            (for [fn-sym covered-fns
                  :let [sealed  (cov/sealed-signature fn-sym)
                        current (sig/signature fn-sym scope-ns-prefixes)]
                  :when (and sealed (not= sealed current))]
              [fn-sym {:sealed-sig  sealed
                       :current-sig current
                       :tested-by   (cov/covered-by fn-sym)}])))))))

#?(:clj
   (defn stale-functions
     "Returns a set of all functions with stale coverage in scope.

      Uses global config if no argument provided."
     ([] (stale-functions (:scope-ns-prefixes (get-config))))
     ([scope-ns-prefixes]
      (set (keys (stale-coverage scope-ns-prefixes))))))

;; =============================================================================
;; Reporting and Statistics
;; =============================================================================

(defn print-coverage-report
  "Print a human-readable coverage report for a function.

   fn-sym - Fully qualified symbol of the function to check
   opts - Optional map with :scope-ns-prefixes (uses global config if not provided)"
  ([fn-sym] (print-coverage-report fn-sym {}))
  ([fn-sym opts]
   #?(:clj
      (let [{:keys [function proof-complete? covered uncovered stale scope-ns-prefixes note]}
            (verify-transitive-coverage fn-sym opts)]
        (println "Coverage Report for:" function)
        (println "=====================================")
        (println "Scope:" (if (seq scope-ns-prefixes) scope-ns-prefixes "(not configured)"))
        (when note (println "Note:" note))
        (println "Proof complete:" (if proof-complete? "YES" "NO"))
        (when (seq covered)
          (println)
          (println "Covered:" (count covered))
          (doseq [f (sort-by str covered)]
            (let [is-stale (contains? stale f)]
              (println (if is-stale "  ~ (STALE)" "  +") f))))
        (when (seq uncovered)
          (println)
          (println "UNCOVERED:" (count uncovered))
          (doseq [f (sort-by str uncovered)]
            (println "  -" f)))
        (when (seq stale)
          (println)
          (println "STALE:" (count stale))
          (doseq [f (sort-by str stale)]
            (println "  ~" f "- needs re-sealing"))))
      :cljs
      (println "Coverage report not available in ClojureScript"))))

(defn uncovered-in-scope
  "Find all guardrailed functions in scope that lack test coverage.

   Uses global config if no argument provided."
  ([] (uncovered-in-scope (:scope-ns-prefixes (get-config))))
  ([scope-ns-prefixes]
   #?(:clj
      (if (empty? scope-ns-prefixes)
        #{}
        (let [all-fns (gr.externs/all-in-scope-functions scope-ns-prefixes)]
          (into #{} (remove cov/covered? all-fns))))
      :cljs #{})))

(defn coverage-stats
  "Returns coverage statistics for functions in the given namespace scope.

   Uses global config if no argument provided.

   Returns map with:
   - :total - total number of guardrailed functions in scope
   - :covered - number with declared coverage
   - :uncovered - number without coverage
   - :stale - number with stale coverage
   - :fresh - number with fresh (up-to-date) coverage
   - :coverage-pct - percentage covered (including stale)"
  ([] (coverage-stats (:scope-ns-prefixes (get-config))))
  ([scope-ns-prefixes]
   #?(:clj
      (if (empty? scope-ns-prefixes)
        {:total 0 :covered 0 :uncovered 0 :stale 0 :fresh 0 :coverage-pct 100.0
         :note  "No scope configured"}
        (let [all-fns         (gr.externs/all-in-scope-functions scope-ns-prefixes)
              total           (count all-fns)
              covered-fns     (filter cov/covered? all-fns)
              covered-count   (count covered-fns)
              uncovered-count (- total covered-count)
              stale-count     (count (filter #(stale? % scope-ns-prefixes) covered-fns))
              fresh-count     (- covered-count stale-count)]
          {:total        total
           :covered      covered-count
           :uncovered    uncovered-count
           :stale        stale-count
           :fresh        fresh-count
           :coverage-pct (if (zero? total)
                           100.0
                           (* 100.0 (/ covered-count total)))}))
      :cljs
      {:total 0 :covered 0 :uncovered 0 :stale 0 :fresh 0 :coverage-pct 100.0})))

;; =============================================================================
;; Reseal Helpers
;; =============================================================================

#?(:clj
   (defn reseal-advice
     "Returns a map of stale functions with advice for resealing.

      For each stale function, returns the new signature to use in the test's
      :covers declaration. This helps developers update their tests when
      implementations change.

      Uses global config if no argument provided.

      Returns: {fn-sym {:current-sig \"abc123\" :test-sym test/my-test}}"
     ([] (reseal-advice (:scope-ns-prefixes (get-config))))
     ([scope-ns-prefixes]
      (if (empty? scope-ns-prefixes)
        {}
        (let [stale (stale-coverage scope-ns-prefixes)]
          (into {}
            (for [[fn-sym {:keys [current-sig tested-by]}] stale]
              [fn-sym {:new-signature current-sig
                       :tested-by     tested-by}])))))))

#?(:clj
   (defn print-reseal-advice
     "Prints human-readable advice for resealing stale tests.

      For each stale function, shows the new signature to use.
      Uses global config if no argument provided."
     ([] (print-reseal-advice (:scope-ns-prefixes (get-config))))
     ([scope-ns-prefixes]
      (let [advice (reseal-advice scope-ns-prefixes)]
        (if (empty? advice)
          (println "No stale functions found - all coverage is up to date.")
          (do
            (println "Stale functions needing re-seal:")
            (println "================================")
            (doseq [[fn-sym {:keys [new-signature tested-by]}] (sort-by (comp str first) advice)]
              (println)
              (println " " fn-sym)
              (println "    New signature:" new-signature)
              (println "    Tested by:" (first tested-by))
              (println "    Update to: {:covers {`" (name fn-sym) " \"" new-signature "\"}}"))
            (println)
            (println "Total:" (count advice) "function(s) need re-sealing")))))))

;; =============================================================================
;; Baseline Export/Import
;; =============================================================================

#?(:clj
   (defn export-baseline
     "Exports current function signatures as a baseline for future comparison.

      Computes signatures for all guardrailed functions in scope and returns
      them as a map suitable for saving to EDN.

      Uses global config if no argument provided.

      Returns: {:generated-at timestamp
                :scope-ns-prefixes #{...}
                :signatures {fn-sym \"abc123\" ...}}"
     ([] (export-baseline (:scope-ns-prefixes (get-config))))
     ([scope-ns-prefixes]
      (if (empty? scope-ns-prefixes)
        {:error :no-scope-configured
         :note  "Configure scope with configure! or pass scope-ns-prefixes"}
        (let [all-fns (gr.externs/all-in-scope-functions scope-ns-prefixes)
              sigs    (into {}
                        (for [fn-sym all-fns
                              :let [s (sig/signature fn-sym scope-ns-prefixes)]
                              :when s]
                          [fn-sym s]))]
          {:generated-at      (java.util.Date.)
           :scope-ns-prefixes scope-ns-prefixes
           :signatures        sigs})))))

#?(:clj
   (defn compare-to-baseline
     "Compares current function signatures against a saved baseline.

      Returns a map showing which functions have changed since the baseline
      was generated. Useful for determining which tests need to run.

      baseline - Map with :signatures {fn-sym signature} as returned by export-baseline

      Returns: {:changed #{fn-sym ...} - functions whose signatures differ
                :added #{fn-sym ...} - functions not in baseline
                :removed #{fn-sym ...} - functions no longer in scope
                :unchanged #{fn-sym ...} - functions with matching signatures}"
     [baseline]
     (let [scope         (or (:scope-ns-prefixes baseline) (:scope-ns-prefixes (get-config)))
           baseline-sigs (:signatures baseline {})
           current-fns   (if (seq scope)
                           (set (gr.externs/all-in-scope-functions scope))
                           #{})
           baseline-fns  (set (keys baseline-sigs))]
       (if (empty? scope)
         {:error :no-scope-configured}
         (let [common-fns    (set/intersection current-fns baseline-fns)
               added-fns     (set/difference current-fns baseline-fns)
               removed-fns   (set/difference baseline-fns current-fns)
               changed-fns   (into #{}
                               (filter (fn [fn-sym]
                                         (not= (get baseline-sigs fn-sym)
                                           (sig/signature fn-sym scope)))
                                 common-fns))
               unchanged-fns (set/difference common-fns changed-fns)]
           {:changed   changed-fns
            :added     added-fns
            :removed   removed-fns
            :unchanged unchanged-fns})))))
