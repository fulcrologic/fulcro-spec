(ns untangled-spec.assertions
  (:require
    [#?(:clj clojure.spec :cljs cljs.spec) :as s]
    [untangled-spec.spec :as us]))

(s/def ::arrow (comp #{"=>" "=fn=>" "=throws=>"} str))
(s/def ::behavior string?)
(s/def ::triple (s/cat
                  :actual   ::us/any
                  :arrow    ::arrow
                  :expected ::us/any))
(s/def ::block (s/cat
                 :behavior (s/? ::behavior)
                 :triples  (s/+ ::triple)))
(s/def ::assertions (s/+ ::block))

(defn fn-assert-expr [msg [f arg :as form]]
  `(let [arg# ~arg
         ;;TODO: catch do-report or prints?
         result# (~f arg#)]
     {:type (if result# :pass :fail)
      :message ~msg :assert-type '~'exec
      :actual arg# :expected '~f}))

(defn eq-assert-expr [msg [exp act :as form]]
  `(let [act# ~act
         exp# ~exp
         result# (= exp# act#)]
     {:type (if result# :pass :fail)
      :message ~msg :assert-type '~'eq
      :actual act# :expected exp#}))

(defn exception-matches? [msg e exp-type & [re f f+]]
  (let [e-msg (or #?(:clj (.getMessage e) :cljs (.-message e)) (str e))]
    (->> (cond
           (some-> (ex-data e) :type (= ::internal))
           {:type :error :extra e-msg
            :actual e :expected "it to throw"}

           (not= exp-type (type e))
           {:type :fail :actual (type e) :expected exp-type
            :extra "exception did not match type"}

           (and re (not (re-find re e-msg)))
           {:type :fail :actual e-msg :expected (str re)
            :extra "exception's message did not match regex"}

           (and f (not (f e)))
           {:type :fail :actual e :expected f+
            :extra "checker function failed"}

           :else {:type :pass :actual "act" :expected "exp"})
         (merge {:message msg
                 :assert-type 'throws?
                 :throwable e}))))

(defn throws-assert-expr [msg [cljs? should-throw exp-type & [re f]]]
  `(try ~should-throw
        (throw (ex-info (str "Expected an '" '~exp-type "' to be thrown!")
                        {:type ::internal}))
        (catch ~(if (not cljs?) (symbol "Throwable") (symbol "js" "Object"))
          e# (exception-matches? ~msg e# ~exp-type ~re ~f '~f))))

(defn assert-expr [disp-key msg form]
  (case (str disp-key)
    "exec"    (fn-assert-expr     msg (rest form))
    "eq"      (eq-assert-expr     msg (rest form))
    "throws?" (throws-assert-expr msg (rest form))
    :else {:type :fail :message msg :actual disp-key
           :expected #{"exec" "eq" "throws?"}}))

(defn triple->assertion [cljs? {:keys [actual arrow expected]}]
  (let [prefix (if cljs? "cljs.test" "clojure.test")
        is (symbol prefix "is")
        msg (str actual " " arrow " " expected)]
    (case arrow
      =>
      `(~is (~'= ~expected ~actual)
            ~msg)

      =fn=>
      (let [checker expected
            arg     actual]
        `(~is (~'exec ~checker ~arg)
              ~msg))

      =throws=>
      (let [should-throw actual
            criteria expected]
        `(~is (~'throws? ~cljs? ~should-throw ~@criteria)
              ~msg))

      (throw (ex-info "invalid arrow" {:arrow arrow})))))

(defn triple? [[left arrow expected]]
  (boolean
    (when (symbol? arrow)
     (re-find #"^=.*>$" (str arrow)))))

(defn forms->blocks [forms]
  (assert (seq forms) "empty assertions")
  (loop [forms forms, blocks []]
    (if (seq forms)
      (let [?str (first forms)
            forms- (if (string? ?str) (rest forms) forms)
            new-block (take-while triple? (partition 3 forms-))
            remain-forms (drop (* 3 (count new-block)) forms-)]
        (assert (seq forms-) "behavior string without trailing assertions")
        (assert (>= (count forms-) 3) "malformed arrow")
        (assert (seq new-block) "behavior string without trailing assertions")
        (recur remain-forms
          (conj blocks
            (if (string? ?str)
              (cons ?str new-block)
              new-block))))
      blocks)))

(defn block->asserts [cljs? {:keys [behavior triples]}]
  (let [prefix (if cljs? "cljs.test" "clojure.test")
        do-report (symbol prefix "do-report")
        asserts (map (partial triple->assertion cljs?) triples)]
    (if behavior
      `(do
         (~do-report {:type :begin-behavior :string ~behavior})
         ~@asserts
         (~do-report {:type :end-behavior :string ~behavior}))
      `(do ~@asserts))))
