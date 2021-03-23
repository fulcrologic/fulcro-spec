(ns fulcro-spec.provided
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [fulcro-spec.impl.macros :as im]
    [fulcro-spec.stub :as stub]
    [fulcro-spec.spec :as ffs]))

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
  (if (symbol? l) l (gensym "arg")))

(defn conformed-stub [env sym arglist result]
  (let [valid?   (if (im/cljs-env? env) `cljs.spec.alpha/valid? `clojure.spec.alpha/valid?)
        get-spec (if (im/cljs-env? env) `cljs.spec.alpha/get-spec `clojure.spec.alpha/get-spec)
        explain  (if (im/cljs-env? env) `cljs.spec.alpha/explain `clojure.spec.alpha/explain)]
    `(fn [~@arglist]
       (let [result# ~result]
         (when-let [spec# (~get-spec (var ~sym))]
           (let [{:keys [~'args ~'ret]} spec#]
             (when (and ~'args (not (~valid? ~'args [~@arglist])))
               (throw (ex-info (str "Mock of " ~(str sym) " was sent arguments that do not conform to spec: " (with-out-str (~explain ~'args [~@arglist]))) {:mock? true})))
             (when (and ~'ret (not (~valid? ~'ret result#)))
               (throw (ex-info (str "Mock of " ~(str sym) " returned a value that does not conform to spec: " (with-out-str (~explain ~'ret result#))) {:mock? true})))))
         result#))))

(defn parse-mock-triple [env conform? {:as triple :keys [under-mock arrow result]}]
  (merge under-mock
    (let [{:keys [params mock-name]} under-mock
          arglist (map literal->gensym params)
          try-result `(try ~result
                        (catch ~(if (im/cljs-env? env) :default 'Exception) t#
                          (throw (ex-info "Uncaught exception in stub!"
                                   {::stub/exception t#}))))]
      {:ntimes        (parse-arrow-count arrow)
       :literals      (mapv symbol->any params)
       :stub-function (if conform?
                        (conformed-stub env mock-name arglist try-result)
                        `(fn [~@arglist] ~try-result))})))

(defn parse-mocks [env conform? mocks]
  (let [parse-steps
        (fn parse-steps [[mock-name steps :as group]]
          (let [symgen (gensym "script")]
            {:script    `(stub/make-script ~(name mock-name)
                           ~(mapv (fn make-step [{:keys [stub-function ntimes literals]}]
                                    `(stub/make-step ~stub-function ~ntimes ~literals))
                              steps))
             :sstub     `(stub/scripted-stub ~symgen)
             :mock-name mock-name
             :symgen    symgen}))]
    (->> mocks
      (map (partial parse-mock-triple env conform?))
      (group-by :mock-name)
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
(s/def ::arrow arrow?)
(s/def ::triple
  (s/cat
    :under-mock ::under-mock
    :arrow ::arrow
    :result ::ffs/any))
(s/def ::mocks
  (s/cat
    :mocks (s/+ ::triple)
    :body (s/+ ::ffs/any)))

(defn provided*
  [env conform-mocks? string forms]
  (let [{:keys [mocks body]} (ffs/conform! ::mocks forms)
        scripts      (parse-mocks env conform-mocks? mocks)
        skip-output? (= :skip-output string)]
    `(im/with-reporting ~(when-not skip-output? {:type :provided :string (str "PROVIDED: " string)})
       (im/try-report "Unexpected"
         (let [~@(mapcat (juxt :symgen :script) scripts)]
           (with-redefs [~@(mapcat (juxt :mock-name :sstub) scripts)]
             (let [result# (do ~@body)]
               (stub/validate-target-function-counts ~(mapv :symgen scripts))
               result#)))))))
