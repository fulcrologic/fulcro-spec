(ns fulcro-spec.coverage
  "Registry for tracking which functions are covered by which tests.

   This namespace provides the foundation for transitive test coverage proofs.
   Tests declare which functions they cover via the :covers metadata in the
   specification macro, and this registry tracks those declarations.

   The :covers metadata is a map of {fn-symbol signature-string} where the
   signature is a short hash of the function's source code at the time the
   test was written/verified. This enables staleness detection when functions
   change.")

;; Registry structure:
;; {fn-sym {:tested-by #{test-syms}
;;          :signature "abc123"}}
(defonce coverage-registry (atom {}))

(defn register-coverage!
  "Register that `test-sym` covers `fn-sym` with optional signature.

   Both test-sym and fn-sym should be fully qualified symbols.
   The signature is a short hash of the function's source at seal time.

   If signature is nil, only the test association is recorded (legacy mode).
   If a different signature is registered for the same function, the new
   signature overwrites the old one (most recent wins)."
  ([test-sym fn-sym]
   (register-coverage! test-sym fn-sym nil))
  ([test-sym fn-sym signature]
   (swap! coverage-registry
     (fn [reg]
       (let [current (get reg fn-sym {:tested-by #{} :signature nil})]
         (assoc reg fn-sym
                    (cond-> (update current :tested-by conj test-sym)
                      signature (assoc :signature signature))))))))

(defn covered-by
  "Returns set of test symbols that cover the given function.

   Returns nil if the function is not in the coverage registry.
   Returns a (possibly empty) set if the function has been registered.

   Use `covered?` for a boolean check of whether the function has coverage."
  [fn-sym]
  (get-in @coverage-registry [fn-sym :tested-by]))

(defn covered?
  "Returns true if fn-sym has at least one test covering it."
  [fn-sym]
  (boolean (seq (covered-by fn-sym))))

(defn sealed-signature
  "Returns the sealed signature for a function, or nil if not sealed.

   The sealed signature is the hash of the function's source code at the
   time the covering test was written/verified."
  [fn-sym]
  (get-in @coverage-registry [fn-sym :signature]))

(defn sealed?
  "Returns true if fn-sym has a sealed signature recorded."
  [fn-sym]
  (boolean (sealed-signature fn-sym)))

(defn clear-registry!
  "Clear the coverage registry. Useful for testing."
  []
  (reset! coverage-registry {}))

(defn all-covered-functions
  "Returns a vector of all function symbols that have declared test coverage."
  []
  (vec (keys @coverage-registry)))

(defn all-sealed-functions
  "Returns a vector of all function symbols that have sealed signatures."
  []
  (into []
    (comp
      (filter (fn [[_sym data]] (:signature data)))
      (map first))
    @coverage-registry))

(defn functions-covered-by
  "Returns a vector of function symbols that the given test covers.

   This is the inverse of `covered-by` - it answers 'what does this test cover?'
   rather than 'what tests cover this function?'.

   Useful for test filtering to determine what functions a test is responsible for."
  [test-sym]
  (into []
    (comp
      (filter (fn [[_fn-sym data]]
                (contains? (:tested-by data) test-sym)))
      (map first))
    @coverage-registry))

(defn coverage-summary
  "Returns a summary map of coverage information.

   Returns:
   - :total-functions - count of functions with any coverage
   - :sealed-functions - count of functions with sealed signatures
   - :coverage-map - the full registry (for debugging)"
  []
  (let [reg          @coverage-registry
        sealed-count (count (filter (fn [[_ data]] (:signature data)) reg))]
    {:total-functions  (count reg)
     :sealed-functions sealed-count
     :coverage-map     reg}))
