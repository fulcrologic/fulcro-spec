(ns fulcro-spec.provided
  (:require
    [clojure.spec.alpha :as s]
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
  (let [nm (name sym)
        number (re-find #"\d+" nm)
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

(defn parse-mock-triple [{:as triple :keys [under-mock arrow result]}]
  (merge under-mock
    (let [{:keys [params]} under-mock
          arglist (map literal->gensym params)]
      {:ntimes        (parse-arrow-count arrow)
       :literals      (mapv symbol->any params)
       :stub-function `(fn [~@arglist] ~result) })))

(defn parse-mocks [mocks]
  (let [parse-steps
        (fn parse-steps [[mock-name steps :as group]]
          (let [symgen (gensym "script")]
            {:script `(stub/make-script ~(name mock-name)
                        ~(mapv (fn make-step [{:keys [stub-function ntimes literals]}]
                                 `(stub/make-step ~stub-function ~ntimes ~literals))
                           steps))
             :sstub `(stub/scripted-stub ~symgen)
             :mock-name mock-name
             :symgen symgen}))]
    (->> mocks
      (map parse-mock-triple)
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
  [cljs? string forms]
  (let [{:keys [mocks body]} (ffs/conform! ::mocks forms)
        scripts (parse-mocks mocks)
        skip-output? (= :skip-output string)]
    `(im/with-reporting ~(when-not skip-output? {:type :provided :string (str "PROVIDED: " string)})
       (im/try-report "Unexpected"
         (let [~@(mapcat (juxt :symgen :script) scripts)]
          (with-redefs [~@(mapcat (juxt :mock-name :sstub) scripts)]
            ~@body
            (stub/validate-target-function-counts ~(mapv :symgen scripts))))))))
