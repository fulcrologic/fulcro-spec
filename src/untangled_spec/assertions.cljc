(ns untangled-spec.assertions
  #?(:cljs
     (:require-macros
       [untangled-spec.assertions :refer [define-assert-exprs!]]))
  (:require
    #?(:clj [clojure.test])
    cljs.test ;; contains multimethod in clojure file
    [clojure.spec.alpha :as s]
    #?(:clj [untangled-spec.impl.macros :as im])
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
         result# (~f arg#)]
     {:type (if result# :pass :fail)
      :message ~msg :assert-type '~'exec
      :actual arg# :expected '~f}))

(defn eq-assert-expr [msg [exp act :as form]]
  `(let [act# ~act
         exp# ~exp
         result# (im/try-report ~msg (= exp# act#))]
     {:type (if result# :pass :fail)
      :message ~msg :assert-type '~'eq
      :actual act# :expected exp#}))

(defn parse-criteria [[tag x]]
  (case tag :sym {:ex-type x} x))

(defn check-error* [msg e & [ex-type regex fn fn-pr]]
  (let [e-msg (or #?(:clj (.getMessage e) :cljs (.-message e)) (str e))]
    (->> (cond
           (some-> (ex-data e) :type (= ::internal))
           {:type :error :extra e-msg
            :actual e :expected "it to throw"}

           (and ex-type (not= ex-type (type e)))
           {:type :fail :actual (type e) :expected ex-type
            :extra "exception did not match type"}

           (and regex (not (re-find regex e-msg)))
           {:type :fail :actual e-msg :expected (str regex)
            :extra "exception's message did not match regex"}

           (and fn (not (fn e)))
           {:type :fail :actual e :expected fn-pr
            :extra "checker function failed"}

           :else {:type :pass :actual "act" :expected "exp"})
      (merge {:message msg
              :assert-type 'throws?
              :throwable e}))))

(defn check-error [msg e criteria & [fn-pr]]
  (apply check-error* msg e
    ((juxt :ex-type :regex :fn :fn-pr)
     (assoc criteria :fn-pr fn-pr))))

(s/def ::ex-type symbol?)
(s/def ::regex ::us/regex)
(s/def ::fn ::us/any)
(s/def ::criteria
  (s/or
    :sym symbol?
    :list (s/cat :ex-type ::ex-type :regex (s/? ::regex) :fn (s/? ::fn))
    :map (s/keys :opt-un [::ex-type ::regex ::fn])))

(defn throws-assert-expr [msg [cljs? should-throw criteria]]
  (let [criteria (parse-criteria (us/conform! ::criteria criteria))]
    `(try ~should-throw
       (throw (ex-info "Expected an error to be thrown!"
                {:type ::internal :criteria ~criteria}))
       (catch ~(if (not cljs?) (symbol "Throwable") (symbol "js" "Object"))
         e# (check-error ~msg e# ~criteria)))))

(defn assert-expr [msg [disp-key & form]]
  (case disp-key
    =       (eq-assert-expr     msg form)
    exec    (fn-assert-expr     msg form)
    throws? (throws-assert-expr msg form)
    {:type :fail :message msg :actual disp-key
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
        `(~is (~'throws? ~cljs? ~should-throw ~criteria)
              ~msg))

      (throw (ex-info "invalid arrow" {:arrow arrow})))))

(defn fix-conform [conformed-assertions]
  ;;see issue: #31
  (if (vector? (second conformed-assertions))
    (vec (cons (first conformed-assertions) (second conformed-assertions)))
    conformed-assertions))

(defn block->asserts [cljs? {:keys [behavior triples]}]
  (let [asserts (map (partial triple->assertion cljs?) triples)]
    `(im/with-reporting ~(when behavior {:type :behavior :string behavior})
       ~@asserts)))

#?(:clj
   (defmacro define-assert-exprs! []
     (let [test-ns (im/if-cljs &env "cljs.test" "clojure.test")
           do-report (symbol test-ns "do-report")
           t-assert-expr (im/if-cljs &env cljs.test/assert-expr clojure.test/assert-expr)
           do-assert-expr
           (fn [args]
             (let [[msg form] (cond-> args (im/cljs-env? &env) rest)]
               `(~do-report ~(assert-expr msg form))))]
       (defmethod t-assert-expr '=       eq-ae     [& args] (do-assert-expr args))
       (defmethod t-assert-expr 'exec    fn-ae     [& args] (do-assert-expr args))
       (defmethod t-assert-expr 'throws? throws-ae [& args] (do-assert-expr args))
       nil)))
(define-assert-exprs!)
