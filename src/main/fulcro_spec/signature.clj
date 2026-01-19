(ns fulcro-spec.signature
  "On-demand signature computation for staleness detection.

   Signatures are short hashes of normalized function source code.
   When a function's implementation changes, its signature changes,
   allowing detection of stale test coverage.

   The signature is computed by:
   1. Extracting full source using clojure.repl/source-fn
   2. Removing docstrings (so doc changes don't invalidate tests)
   3. Normalizing whitespace (so formatting changes don't invalidate tests)
   4. Hashing with SHA-256 and taking first 6 characters

   Signature format:
   - LEAF functions (no in-scope callees): Single-field \"xxxxxx\"
   - NON-LEAF functions: Two-field \"xxxxxx,yyyyyy\" where:
     - xxxxxx: 6-char hash of the function's own source
     - yyyyyy: 6-char hash of all transitive callees' signatures

   The `signature` function automatically detects leaf vs non-leaf and
   returns the appropriate format. Use it for all signature needs.

   Performance options (Java system properties):
   - fulcro-spec.auto-skip: Enable auto-skipping of already-checked tests
   - fulcro-spec.sigcache: Cache signatures for duration of JVM"
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.repl]
    [clojure.string :as str]
    [com.fulcrologic.guardrails.impl.externs :as gr.externs])
  (:import
    (java.nio.charset StandardCharsets)
    (java.security MessageDigest)))

;; =============================================================================
;; Content Normalization
;; =============================================================================

(defn- find-string-end
  "Finds the end position of a string starting at idx (after opening quote).
   Returns the index after the closing quote, or nil if not found.
   Handles escape sequences correctly."
  [s idx]
  (loop [i idx]
    (when (< i (count s))
      (let [ch (get s i)]
        (cond
          (= ch \\) (recur (+ i 2))                         ; Skip escaped char
          (= ch \") (inc i)                                 ; Found closing quote
          :else (recur (inc i)))))))

(defn- skip-whitespace
  "Returns index of first non-whitespace character starting from idx."
  [s idx]
  (loop [i idx]
    (if (and (< i (count s))
          (Character/isWhitespace (char (get s i))))
      (recur (inc i))
      i)))

(defn- find-matching-bracket
  "Finds the closing bracket matching the opening bracket at idx.
   Returns index after the closing bracket, or nil if not found.
   Handles nested brackets and strings correctly."
  [s idx]
  (let [open-ch  (get s idx)
        close-ch (case open-ch
                   \[ \]
                   \( \)
                   \{ \}
                   nil)]
    (when close-ch
      (loop [i     (inc idx)
             depth 1]
        (when (< i (count s))
          (let [ch (get s i)]
            (cond
              (= ch \")
              (if-let [end (find-string-end s (inc i))]
                (recur end depth)
                nil)

              (= ch open-ch)
              (recur (inc i) (inc depth))

              (= ch close-ch)
              (if (= depth 1)
                (inc i)
                (recur (inc i) (dec depth)))

              :else
              (recur (inc i) depth))))))))

(defn- remove-docstring-from-def
  "Removes docstring from a def* form (defn, defn-, >defn, etc.) in source text.

   Handles both forms:
   - (defn name \"docstring\" [args] body)
   - (defn name [args] \"docstring\" body)

   Returns source with docstrings removed."
  [s]
  (let [len (count s)]
    (loop [i      0
           result (StringBuilder.)]
      (if (>= i len)
        (str result)
        (let [ch (get s i)]
          (cond
            ;; Handle strings not in def position - preserve them
            (= ch \")
            (if-let [end (find-string-end s (inc i))]
              (do
                (.append result (subs s i end))
                (recur end result))
              (recur (inc i) result))

            ;; Look for def forms: (def..., (>def...
            (and (= ch \()
              (< (inc i) len)
              (let [next-ch (get s (inc i))]
                (or (= next-ch \d)                          ; (def...
                  (= next-ch \>))))                         ; (>def...
            (let [;; Skip the > if present
                  def-start (if (= \> (get s (inc i))) (+ i 2) (inc i))
                  def-end   (loop [j def-start]
                              (if (and (< j len)
                                    (let [c (get s j)]
                                      (or (Character/isLetterOrDigit (char c))
                                        (= c \-)
                                        (= c \>))))         ; for >defn
                                (recur (inc j))
                                j))]
              (if (and (>= (- def-end def-start) 3)         ; At least "def"
                    (str/starts-with? (subs s def-start (min (+ def-start 3) len)) "def")
                    (< def-end len)
                    (Character/isWhitespace (get s def-end)))
                (let [after-def  (skip-whitespace s def-end)
                      ;; Skip the function name
                      name-end   (loop [j after-def]
                                   (if (and (< j len)
                                         (let [c (get s j)]
                                           (not (Character/isWhitespace (char c)))))
                                     (recur (inc j))
                                     j))
                      after-name (skip-whitespace s name-end)]
                  ;; Check for docstring right after name: (defn name "doc" ...)
                  (if (and (< after-name len)
                        (= \" (get s after-name)))
                    (if-let [doc-end (find-string-end s (inc after-name))]
                      (do
                        (.append result (subs s i after-name))
                        (recur doc-end result))
                      (do
                        (.append result ch)
                        (recur (inc i) result)))
                    ;; Check for docstring after args: (defn name [args] "doc" ...)
                    (if (and (< after-name len)
                          (= \[ (get s after-name)))
                      (if-let [args-end (find-matching-bracket s after-name)]
                        (let [after-args (skip-whitespace s args-end)]
                          (if (and (< after-args len)
                                (= \" (get s after-args)))
                            (if-let [doc-end (find-string-end s (inc after-args))]
                              (do
                                (.append result (subs s i after-args))
                                (recur doc-end result))
                              (do
                                (.append result ch)
                                (recur (inc i) result)))
                            (do
                              (.append result ch)
                              (recur (inc i) result))))
                        (do
                          (.append result ch)
                          (recur (inc i) result)))
                      (do
                        (.append result ch)
                        (recur (inc i) result)))))
                (do
                  (.append result ch)
                  (recur (inc i) result))))

            :else
            (do
              (.append result ch)
              (recur (inc i) result))))))))

(defn- normalize-content
  "Normalizes source code content for semantic comparison.

   Removes docstrings and normalizes whitespace so that:
   - Implementation changes ARE detected
   - Docstring changes are NOT detected
   - Whitespace/formatting changes are NOT detected"
  [source-text]
  (when source-text
    (try
      (let [without-docs (remove-docstring-from-def source-text)
            normalized   (-> without-docs
                           (str/replace #"\s+" " ")
                           str/trim)]
        normalized)
      (catch Exception _e
        source-text))))

;; =============================================================================
;; Hashing
;; =============================================================================

(defn- sha256
  "Generates a SHA256 hash of the input string."
  [^String s]
  (when s
    (let [digest     (MessageDigest/getInstance "SHA-256")
          hash-bytes (.digest digest (.getBytes s StandardCharsets/UTF_8))]
      (apply str (map #(format "%02x" %) hash-bytes)))))

(defn- hash-content
  "Generates a content hash for normalized source text.
   Returns full SHA256 hex string."
  [source-text]
  (some-> source-text
    normalize-content
    sha256))

;; =============================================================================
;; Self-Signature (internal helper)
;; =============================================================================

(defn- absolute-path?
  "Returns true if the path string appears to be an absolute filesystem path."
  [^String path]
  (and path
    (or (.startsWith path "/")
      (and (> (count path) 2)
        (= \: (get path 1))))))                             ;; Windows: C:\...

(defn- source-from-absolute-path
  "Reads source from an absolute file path starting at the given line.
   Returns the source text of the first readable form, or nil on failure.

   This handles the case where files were loaded via `load-file` (common in IDE
   workflows) which sets :file metadata to an absolute path that
   clojure.repl/source-fn can't resolve via the classpath."
  [^String filepath line]
  (try
    (with-open [rdr (java.io.LineNumberReader. (java.io.FileReader. filepath))]
      (dotimes [_ (dec line)] (.readLine rdr))
      (let [text (StringBuilder.)
            pbr  (proxy [java.io.PushbackReader] [rdr]
                   (read [] (let [i (proxy-super read)]
                              (.append text (char i))
                              i)))
            read-opts (if (.endsWith filepath "cljc") {:read-cond :allow} {})]
        (read read-opts (java.io.PushbackReader. pbr))
        (str text)))
    (catch Exception _e
      nil)))

(defn- get-source
  "Gets source code for a var, handling both classpath-relative and absolute paths.

   First tries clojure.repl/source-fn (works for classpath-loaded files).
   Falls back to direct filesystem read for absolute paths (load-file scenario)."
  [fn-sym]
  (or (clojure.repl/source-fn fn-sym)
    (when-let [v (resolve fn-sym)]
      (let [{:keys [file line]} (meta v)]
        (when (and (absolute-path? file) line)
          (source-from-absolute-path file line))))))

(defn self-signature
  "Computes a short signature (first 6 chars of SHA256) for a function's own source.

   This is an internal helper - use `signature` for the public API which
   automatically handles leaf vs non-leaf functions.

   Args:
     fn-sym - Fully qualified symbol of the function

   Returns:
     6-character signature string, or nil if source not available."
  [fn-sym]
  (when-let [source (get-source fn-sym)]
    (when-let [hash (hash-content source)]
      (subs hash 0 (min 6 (count hash))))))

;; =============================================================================
;; Property Checks (cached at first access for zero overhead)
;; =============================================================================

(def ^:private auto-skip-enabled?*
  "Cached result of auto-skip property check.
   Property is enabled if set to any value (including empty string)."
  (delay (some? (System/getProperty "fulcro-spec.auto-skip"))))

(def ^:private sigcache-enabled?*
  "Cached result of sigcache property check.
   Property is enabled if set to any value (including empty string)."
  (delay (some? (System/getProperty "fulcro-spec.sigcache"))))

(defn auto-skip-enabled?
  "Returns true if the fulcro-spec.auto-skip system property is set.
   Presence of the property (even with empty value) enables the feature.
   Result is cached at first call for zero overhead on subsequent checks."
  []
  @auto-skip-enabled?*)

(defn sigcache-enabled?
  "Returns true if the fulcro-spec.sigcache system property is set.
   Presence of the property (even with empty value) enables the feature.
   Result is cached at first call for zero overhead on subsequent checks."
  []
  @sigcache-enabled?*)

;; =============================================================================
;; Signature Caching (only used when sigcache enabled)
;; =============================================================================

;; Cache for computed signatures. Only populated when sigcache is enabled.
;; Valid for duration of JVM process - intended for full test suite runs,
;; not interactive development where code changes between test runs.
(defonce ^:private signature-cache (atom {}))

(defn- get-self-signature
  "Gets self-only signature for fn-sym, using cache if -Dfulcro-spec.sigcache=true."
  [fn-sym]
  (if (sigcache-enabled?)
    (or (get @signature-cache fn-sym)
      (let [sig (self-signature fn-sym)]
        (swap! signature-cache assoc fn-sym sig)
        sig))
    (self-signature fn-sym)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn leaf?
  "Returns true if the function has no in-scope transitive callees.

   A leaf function is one that doesn't call any other guardrailed functions
   within the given namespace scope. Leaf functions use single-field signatures
   because there are no dependencies to track.

   Args:
     fn-sym - Fully qualified symbol of the function
     scope-ns-prefixes - Set of namespace prefix strings to include

   Example:
     (leaf? 'myapp.utils/format-date #{\"myapp\"})
     ;; => true (if it calls no other myapp.* guardrailed functions)"
  [fn-sym scope-ns-prefixes]
  (let [all-callees (gr.externs/transitive-calls fn-sym scope-ns-prefixes)
        callees     (disj all-callees fn-sym)]
    (empty? callees)))

;; Cache for loaded config (to avoid repeated file reads)
(defonce ^:private loaded-config* (atom nil))

(defn- load-scope-from-config
  "Loads scope-ns-prefixes from .fulcro-spec.edn file.
   Returns empty set if file doesn't exist or can't be read.
   Result is cached for the lifetime of the JVM."
  []
  (or @loaded-config*
    (let [config (try
                   (let [f (io/file ".fulcro-spec.edn")]
                     (when (.exists f)
                       (edn/read-string (slurp f))))
                   (catch Exception _ nil))]
      (reset! loaded-config* (or (:scope-ns-prefixes config) #{}))
      @loaded-config*)))

(defn signature
  "Returns the appropriate signature for a function based on its call graph.

   For LEAF functions (no in-scope callees):
     Returns single-field: \"xxxxxx\"
     - Just the 6-char hash of the function's own source

   For NON-LEAF functions (has in-scope callees):
     Returns two-field: \"xxxxxx,yyyyyy\"
     - xxxxxx: 6-char hash of the function's own source
     - yyyyyy: 6-char hash of all transitive callees' signatures (sorted)

   This design ensures:
   - Leaf functions don't have redundant ',000000' suffix
   - Non-leaf functions MUST have the callee hash (single-field rejected)
   - Changes to any function in the call chain invalidate dependent tests

   Args:
     fn-sym - Fully qualified symbol of the function
     scope-ns-prefixes - Optional set of namespace prefix strings to include.
                         If not provided, reads from .fulcro-spec.edn config file.

   Uses signature caching if -Dfulcro-spec.sigcache=true

   Examples:
     ;; Using config from .fulcro-spec.edn (1-arity)
     (signature 'myapp.utils/format)
     ;; => \"a1b2c3\"

     ;; With explicit scope (2-arity)
     (signature 'myapp.orders/process #{\"myapp\"})
     ;; => \"a1b2c3,d4e5f6\""
  ([fn-sym]
   (signature fn-sym (load-scope-from-config)))
  ([fn-sym scope-ns-prefixes]
   (when-let [self-sig (get-self-signature fn-sym)]
     (let [all-callees (gr.externs/transitive-calls fn-sym scope-ns-prefixes)
           callees     (disj all-callees fn-sym)
           sorted      (sort-by str callees)
           callee-sigs (keep get-self-signature sorted)]
       (if (seq callee-sigs)
         ;; Non-leaf: two-field format with callee hash
         (let [combined    (str/join "," callee-sigs)
               callees-sig (subs (sha256 combined) 0 6)]
           (str self-sig "," callees-sig))
         ;; Leaf: single-field format (no callees)
         self-sig)))))

;; =============================================================================
;; Test Skipping Predicate
;; =============================================================================

(defn- sigs-match?
  "Returns true if sealed-sig matches current-sig, handling legacy compatibility.
   Legacy format used 'xxx,000000' for leaf functions; new format uses just 'xxx'.
   This function considers them equivalent."
  [sealed-sig current-sig]
  (or (= sealed-sig current-sig)
    ;; Handle legacy: sealed has ,000000 but current is single-field (leaf)
    (and (str/ends-with? (str sealed-sig) ",000000")
      (= (subs sealed-sig 0 (- (count sealed-sig) 7)) current-sig))))

(defn already-checked?
  "Returns true if this test's coverage declarations indicate it has already
   been verified and nothing has changed since.

   IMPORTANT: Zero overhead when disabled - property check short-circuits first.
   No expensive signature computation occurs unless auto-skip is enabled.

   A test is considered 'already checked' when:
   1. -Dfulcro-spec.auto-skip=true
   2. scope-ns-prefixes configured (passed as argument)
   3. Every covered function's sealed signature matches current signature

   This means: the test was previously run, passed, and the developer sealed
   it by recording the signatures in the :covers metadata. Since then, neither
   the covered functions nor any functions they call have changed.

   For fast full-suite runs, also enable -Dfulcro-spec.sigcache=true

   Args:
     covers-map - Map of {fn-symbol sealed-signature} from test's :covers metadata
     scope-ns-prefixes - Set of namespace prefix strings for transitive analysis

   Returns:
     true if test can be skipped, false otherwise"
  [covers-map scope-ns-prefixes]
  ;; Property check FIRST - returns false immediately if disabled (zero overhead)
  (and (auto-skip-enabled?)
    (seq scope-ns-prefixes)
    (every? (fn [[fn-sym sealed-sig]]
              (sigs-match? sealed-sig (signature fn-sym scope-ns-prefixes)))
      covers-map)))
