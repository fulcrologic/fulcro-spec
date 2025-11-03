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

(defn fix-conform [conformed-assertions]
  ;;see issue: #31
  (if (vector? (second conformed-assertions))
    (vec (cons (first conformed-assertions) (second conformed-assertions)))
    conformed-assertions))

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
