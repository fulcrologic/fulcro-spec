(ns fulcro-spec.provided
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [fulcro-spec.impl.macros :as im]
    [fulcro-spec.instrument :as instrument]
    [fulcro-spec.spec :as ffs]
    [fulcro-spec.stub :as stub]))

(defn format-mock-error
  "Formats a clear error message for mock validation failures.

   Arguments:
   - sym: The symbol of the mocked function
   - error-type: The type of error (e.g., ::instrument/invalid-spec-input)
   - error-data: The error details map from the instrument layer

   Returns a human-readable error message describing what went wrong."
  [sym error-type error-data]
  (let [{:keys [args value spec problems original-error input output]} error-data]
    (case error-type
      ;; Spec-based errors
      ::instrument/invalid-spec-input
      (let [explain-str (when problems
                          (with-out-str
                            (clojure.spec.alpha/explain-out problems)))]
        (str "The mock for `" sym "` was called with arguments that don't match the expected spec.\n"
          "Arguments received: " (pr-str args) "\n"
          (when explain-str
            (str "Spec failure:\n" explain-str))))

      ::instrument/invalid-spec-output
      (let [explain-str (when problems
                          (with-out-str
                            (clojure.spec.alpha/explain-out problems)))]
        (str "The mock for `" sym "` returned a value that doesn't match the expected spec.\n"
          "Return value: " (pr-str value) "\n"
          (when explain-str
            (str "Spec failure:\n" explain-str))))

      ::instrument/invalid-spec-fn
      (str "The mock for `" sym "` failed the :fn spec check.\n"
        "Arguments: " (pr-str args) "\n"
        "Return value: " (pr-str value))

      ::instrument/spec-resolution-error
      (str "The mock for `" sym "` could not be validated because the schema/spec failed to resolve.\n"
        (when original-error
          (str "Error: " original-error "\n"))
        "This typically means a referenced schema or spec doesn't exist or wasn't loaded.")

      ;; Malli-based errors
      ::instrument/invalid-input
      (str "The mock for `" sym "` was called with arguments that don't match the Malli schema.\n"
        "Arguments received: " (pr-str args) "\n"
        "Expected input schema: " (pr-str input))

      ::instrument/invalid-output
      (str "The mock for `" sym "` returned a value that doesn't match the Malli schema.\n"
        "Return value: " (pr-str value) "\n"
        "Expected output schema: " (pr-str output))

      ::instrument/invalid-arity
      (let [{:keys [arity arities]} error-data]
        (str "The mock for `" sym "` was called with wrong arity.\n"
          "Called with " arity " argument(s)\n"
          "Expected arities: " (pr-str arities)))

      ::instrument/invalid-guard
      (str "The mock for `" sym "` failed the guard condition.\n"
        "Arguments: " (pr-str args) "\n"
        "Return value: " (pr-str value))

      ::instrument/malli-error
      (str "The mock for `" sym "` validation encountered a Malli internal error.\n"
        (when original-error
          (str "Error: " original-error "\n"))
        "This may indicate the schema is malformed or references an unregistered type.")

      ;; Default fallback
      (str "Mock validation failed for `" sym "`: " error-type
        (when original-error (str "\nError: " original-error))))))

(defn parse-arrow-count
  "parses how many times the mock/stub should be called with.
   * => implies 1+,
   * =Ax=> implies exactly A times.
   Provided arrow counts cannot be zero because mocking should
      not be a negative assertion, but a positive one.
   IE: verify that what you want to be called is called instead,
      or that, if necessary, it should throw an error if called."
  [sym]
  (let [nm           (name sym)
        number       (re-find #"\d+" nm)
        not-zero-msg "Arrow count must not be zero in provided clauses."]
    (assert (re-find #"^=" nm) "Arrows must start with = (try =#x=>)")
    (assert (re-find #"=>$" nm) "Arrows must end with => (try =#x=>)")
    (cond
      (= "=>" nm) :many
      (= "0" number) (assert false not-zero-msg)
      number (Integer/parseInt number))))

(defn symbol->any [s]
  (cond
    (= '& s) ::stub/&_
    (symbol? s) ::stub/any
    :else s))

(defn literal->gensym [l]
  (if (or (not (symbol? l))
        (= '_ l))
    (gensym "arg")
    l))

(defn param-sym [p]
  (cond
    (not (symbol? p)) ::stub/literal
    (= '_ p) ::stub/ignored
    (= '& p) ::stub/&_
    (str/starts-with? (str p) "_") ::stub/ignored
    :else (str p)))

(defn duplicates [coll]
  (let [freqs (frequencies coll)]
    (distinct (filter #(< 1 (freqs %)) coll))))

(defn assert-no-duplicate-arglist-symbols! [arglist]
  (when-let [dupes (seq (duplicates arglist))]
    (throw (ex-info "Found duplicate symbols, cannot resolve automatically! Disambiguate them by giving them unique names."
             {:arglist    arglist
              :duplicates dupes})))
  :ok)

(defn collect-arglist [arglist]
  (let [[syms varargs] (split-with (comp not #{'&}) arglist)]
    (if (and (empty? syms) (seq varargs))
      (second varargs)
      (vec
        (concat syms
          (drop 1 varargs))))))

;; Store the original spec-only conformed-stub for fallback
(defn spec-only-conformed-stub
  "Original spec-only stub validation (fallback when no guardrails schema)."
  [env sym arglist result]
  (let [valid?   (if (im/cljs-env? env) `cljs.spec.alpha/valid? `clojure.spec.alpha/valid?)
        get-spec (if (im/cljs-env? env) `cljs.spec.alpha/get-spec `clojure.spec.alpha/get-spec)
        explain  (if (im/cljs-env? env) `cljs.spec.alpha/explain `clojure.spec.alpha/explain)]
    (assert-no-duplicate-arglist-symbols! arglist)
    `(fn [~@arglist]
       (let [result# ~result]
         (when-let [spec# (~get-spec (var ~sym))]
           (let [{args# :args ret# :ret} spec#
                 arglist# ~(collect-arglist arglist)]
             (when (and args# (not (~valid? args# arglist#)))
               (throw (ex-info (str "Mock of " ~(str sym) " was sent arguments that do not conform to spec: " (with-out-str (~explain args# arglist#))) {:mock? true})))
             (when (and ret# (not (~valid? ret# result#)))
               (throw (ex-info (str "Mock of " ~(str sym) " returned a value that does not conform to spec: " (with-out-str (~explain ret# result#))) {:mock? true})))))
         result#))))

(defn conformed-stub
  "Creates a stub that validates args/return against guardrails specs.
   Checks for Malli schema first (via :malli/schema metadata), then Clojure Spec fspec.
   Falls back to original spec-only behavior if neither is found."
  [env sym arglist result]
  (let [cljs?    (im/cljs-env? env)
        spec-get (if cljs? 'cljs.spec.alpha/get-spec 'clojure.spec.alpha/get-spec)
        ;; In CLJ, use detailed format-mock-error; in CLJS, use simple inline message
        format-error (if cljs?
                       `(fn [sym# error-type# error-data#]
                          (str "Mock validation failed for `" sym# "`: " error-type#
                            (when-let [e# (:original-error error-data#)]
                              (str "\nError: " e#))))
                       `fulcro-spec.provided/format-mock-error)]
    (assert-no-duplicate-arglist-symbols! arglist)
    `(let [stub# (fn [~@arglist] ~result)
           format-error# ~format-error]
       (cond
         ;; Check for Malli schema first
         (get (meta (var ~sym)) :malli/schema)
         (let [schema# (get (meta (var ~sym)) :malli/schema)]
           (fulcro-spec.instrument/malli-instrument stub# schema#
             (fn [error-type# error-data#]
               (when stub/*validation-problems*
                 (swap! stub/*validation-problems* conj
                   {:message    (format-error# '~sym error-type# error-data#)
                    :error-type error-type#
                    :details    error-data#})))))

         ;; Check for Spec fspec
         (~spec-get (var ~sym))
         (fulcro-spec.instrument/spec-instrument stub# (var ~sym)
           (fn [error-type# error-data#]
             (when stub/*validation-problems*
               (swap! stub/*validation-problems* conj
                 {:message    (format-error# '~sym error-type# error-data#)
                  :error-type error-type#
                  :details    error-data#}))))

         ;; No schema/spec - just return the plain stub
         :else stub#))))

(defn try-stub [env body]
  `(try ~body
        (catch ~(if (im/cljs-env? env) :default 'Exception) t#
          (throw (ex-info "Uncaught exception in stub!"
                   {::stub/exception t#})))))

(defn parse-mock-triple
  [env conform?
   {:as triple :keys [under-mock arrow result behavior]}]
  (merge under-mock
    (let [{:keys [params mock-name]} under-mock]
      {:behavior      behavior
       :ntimes        (parse-arrow-count arrow)
       :literals      (mapv symbol->any params)
       :mock-arglist  (mapv param-sym params)
       :stub-function (let [arglist (map literal->gensym params)]
                        (if conform?
                          (conformed-stub env mock-name arglist (try-stub env result))
                          `(fn [~@arglist] ~(try-stub env result))))})))

(defn parse-mock-block [env conform? {:keys [behavior triples]}]
  (map (partial parse-mock-triple env conform?)
    (map #(assoc % :behavior behavior) triples)))

(defn emit-mock-fn [env mock-name]
  (if (im/cljs-env? env)
    mock-name
    `(deref (var ~mock-name))))

(defn parse-mocks [env conform? mocks]
  (let [parse-steps
        (fn parse-steps [[[behavior mock-name] steps :as group]]
          (let [symgen (gensym "script")]
            {:script    `(stub/make-script ~(emit-mock-fn env mock-name)
                           ~(mapv (fn make-step [{:keys [stub-function ntimes literals mock-arglist]}]
                                    `(stub/make-step ~stub-function ~ntimes ~literals ~mock-arglist))
                              steps))
             :sstub     `(stub/scripted-stub ~symgen)
             :mock-name mock-name
             :symgen    symgen
             :behavior  behavior}))]
    (->> mocks
      (mapcat (partial parse-mock-block env conform?))
      (group-by (juxt :behavior :mock-name))
      (map parse-steps))))

(defn arrow? [sym]
  (and (symbol? sym)
    (re-find #"^=" (name sym))
    (re-find #"=>$" (name sym))))

(s/def ::under-mock
  (s/and list?
    (s/cat
      :mock-name symbol?
      :params (s/* ::ffs/any))))
(s/def ::behavior string?)
(s/def ::arrow arrow?)
(s/def ::triple
  (s/cat
    :under-mock ::under-mock
    :arrow ::arrow
    :result ::ffs/any))
(s/def ::mock-block
  (s/cat
    :behavior (s/? ::behavior)
    :triples (s/+ ::triple)))
(s/def ::mocks
  (s/cat
    :mocks (s/+ ::mock-block)
    :body (s/+ ::ffs/any)))

(defn provided*
  [env conform-mocks? string forms]
  (let [{:keys [mocks body]} (ffs/conform! ::mocks forms)
        scripts      (parse-mocks env conform-mocks? mocks)
        skip-output? (= :skip-output string)]
    `(im/with-reporting ~(when-not skip-output? {:type :provided :string (str "PROVIDED: " string)})
       (im/try-report "Unexpected"
         (let [~@(mapcat (juxt :symgen :script) scripts)]
           ~@(map
               (fn [s] `(im/begin-reporting ~{:type :behavior :string s}))
               (distinct (keep :behavior scripts)))
           (with-redefs [~@(mapcat (juxt :mock-name :sstub) scripts)]
             (let [result# (binding [stub/*script-by-fn*
                                     ~(into {}
                                        (map (juxt
                                               #(emit-mock-fn env (:mock-name %))
                                               :symgen))
                                        scripts)]
                             ~@body)]
               (stub/validate-target-function-counts ~(mapv :symgen scripts))
               ~@(map
                   (fn [s] `(im/end-reporting ~{:type :behavior :string s}))
                   (distinct (keep :behavior scripts)))
               result#)))))))

(defn extract-mocked-symbols
  "Extract the function symbols being mocked from mock triple forms.
   Returns a vector of symbols (not necessarily qualified - they're as written in the mock)."
  [forms]
  (let [{:keys [mocks]} (ffs/conform! ::mocks forms)]
    (->> mocks
      (mapcat :triples)
      (map (comp :mock-name :under-mock))
      (distinct)
      (vec))))
