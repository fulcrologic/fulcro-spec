(ns fulcro-spec.assertions
  #?(:cljs
     (:require-macros fulcro-spec.assertions))
  (:require
    #?(:clj [clojure.test])
    cljs.test                                               ;; contains multimethod in clojure file
    [clojure.spec.alpha :as s]
    #?(:clj
       [fulcro-spec.impl.macros :as im])
    [fulcro-spec.spec :as fss]
    [fulcro-spec.check :as fs.check]
    [fulcro-spec.impl.check :as fs.impl.check]))

(s/def ::arrow (comp #{"=>" "=fn=>" "=throws=>" "=check=>"} str))
(s/def ::behavior string?)
(s/def ::triple (s/cat
                  :actual ::fss/any
                  :arrow ::arrow
                  :expected ::fss/any))
(s/def ::block (s/cat
                 :behavior (s/? ::behavior)
                 :triples (s/+ ::triple)))
(s/def ::assertions (s/+ ::block))

(defn fn-assert-expr [msg [f arg :as form]]
  `(let [arg#    ~arg
         result# (~f arg#)]
     {:type        (if result# :pass :fail)
      :message     ~msg
      :assert-type '~'exec
      ::actual     arg#
      ::expected   ~f
      :actual      '~form
      :expected    '~form}))

(defn eq-assert-expr [msg [exp act :as form]]
  `(let [act#    ~act
         exp#    ~exp
         result# (im/try-report ~msg (= exp# act#))]
     {:type    (if result# :pass :fail)
      :message ~msg :assert-type '~'eq
      :actual  act# :expected exp#}))

(defn assert-expr [msg [disp-key & form]]
  (cond
    (= '= disp-key) (eq-assert-expr msg form)
    (= 'exec disp-key) (fn-assert-expr msg form)
    :else {:type     :fail
           :message  msg
           :actual   (cons disp-key form)
           :expected (list #{"exec" "check" "eq"} disp-key)}))

#?(:clj
   (defn triple->assertion [cljs? {:keys [actual arrow expected]}]
     (let [prefix (if cljs? "cljs.test" "clojure.test")
           is     (symbol prefix "is")
           msg    (str (pr-str actual) " " arrow " " (pr-str expected))]
       (case arrow
         =>
         `(~is (~'= ~expected ~actual)
            ~msg)

         =fn=>
         (let [checker expected
               arg     actual]
           `(~is (~'exec ~checker ~arg)
              ~msg))

         =check=>
         `(~is (~'check ~expected ~actual)
            ~msg)

         =throws=>
         (let [cls (if cljs? :default Throwable)]
           (cond
             (or (symbol? expected) (= :default expected))
             `(~is (~'thrown? ~expected ~actual)
                ~msg)
             (instance? java.util.regex.Pattern expected)
             `(~is (~'thrown-with-msg? ~cls ~expected ~actual)
                ~msg)
             :else
             `(~is (~'check (fs.check/throwable* ~expected)
                     (try ~actual
                          (catch ~cls e# e#)))
                ~msg)))

         (throw (ex-info "invalid arrow" {:arrow arrow}))))))

(defn- arrow-sym?
  "Returns true if `x` is one of the assertion arrow symbols."
  [x]
  (and (symbol? x) (contains? #{"=>" "=fn=>" "=throws=>" "=check=>"} (str x))))

(defn parse-assertions
  "Parses assertion `forms` in a single linear pass, replacing the spec-based
   conformance that suffers from backtracking. Returns a vector of blocks, each
   a map with optional `:behavior` string and `:triples` vector.

   A string is a behavior label when the next form is NOT an arrow (meaning the
   string cannot be the :actual of a triple). Otherwise the string is the :actual
   of the next triple."
  [forms]
  (let [v   (vec forms)
        len (count v)]
    (loop [i       0
           block   {:triples []}
           result  []]
      (if (>= i len)
        (if (seq (:triples block))
          (conj result block)
          result)
        (let [form (nth v i)]
          (if (and (string? form)
                (or (>= (inc i) len)
                  (not (arrow-sym? (nth v (inc i))))))
            ;; This string is a behavior label for a new block
            (let [result (if (seq (:triples block))
                           (conj result block)
                           result)]
              (recur (inc i)
                {:behavior form :triples []}
                result))
            ;; This form is the :actual of a triple
            (do
              (when (> (+ i 3) len)
                (throw (ex-info (str "Incomplete assertion: expected [actual arrow expected] but "
                                  (- len i) " form(s) remain starting at: " (pr-str form))
                         {:forms (subvec v i len)})))
              (let [arrow (nth v (inc i))]
                (when-not (arrow-sym? arrow)
                  (throw (ex-info (str "Invalid assertion arrow: " (pr-str arrow)
                                    ". Expected one of: => =fn=> =throws=> =check=>")
                           {:arrow arrow :actual form})))
                (let [triple {:actual   form
                              :arrow    arrow
                              :expected (nth v (+ i 2))}]
                  (recur (+ i 3)
                    (update block :triples conj triple)
                    result))))))))))

#?(:clj
   (defn block->asserts [cljs? {:keys [behavior triples]}]
     (let [asserts (map (partial triple->assertion cljs?) triples)]
       `(im/with-reporting ~{:type :behavior :string (if (empty? behavior) "unmarked" behavior)}
          ~@asserts))))

#?(:clj
   (do
     (defmethod cljs.test/assert-expr '= [env msg form]
       `(cljs.test/do-report ~(assert-expr msg form)))
     (defmethod cljs.test/assert-expr 'exec [env msg form]
       `(cljs.test/do-report ~(assert-expr msg form)))
     (defmethod cljs.test/assert-expr 'check [env msg form]
       (fs.impl.check/check-expr true msg form))
     (defmethod clojure.test/assert-expr '= [msg form]
       `(clojure.test/do-report ~(assert-expr msg form)))
     (defmethod clojure.test/assert-expr 'exec [msg form]
       `(clojure.test/do-report ~(assert-expr msg form)))
     (defmethod clojure.test/assert-expr 'check [msg form]
       (fs.impl.check/check-expr false msg form))))
