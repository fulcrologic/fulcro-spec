(ns fulcro-spec.assertions
  #?(:cljs
     (:require-macros fulcro-spec.assertions))
  (:require
    #?(:clj [clojure.test])
    cljs.test                                               ;; contains multimethod in clojure file
    [clojure.spec.alpha :as s]
    #?(:clj
       [fulcro-spec.impl.macros :as im])
    [fulcro-spec.spec :as fss]))

(s/def ::arrow (comp #{"=>" "=fn=>" "=throws=>"} str))
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
     {:type    (if result# :pass :fail)
      :message ~msg :assert-type '~'exec
      :actual  arg# :expected '~f}))

(defn eq-assert-expr [msg [exp act :as form]]
  `(let [act#    ~act
         exp#    ~exp
         result# (im/try-report ~msg (= exp# act#))]
     {:type    (if result# :pass :fail)
      :message ~msg :assert-type '~'eq
      :actual  act# :expected exp#}))

(defn parse-criteria [[tag x]]
  (case tag :sym {:ex-type x} x))

(defn check-error* [msg e & [ex-type regex fn fn-pr]]
  (let [e-msg (or #?(:clj (.getMessage e) :cljs (.-message e)) (str e))]
    (->> (cond
           (some-> (ex-data e) :type (= ::internal))
           {:type   :error :extra e-msg
            :actual e :expected "it to throw"}

           (and ex-type (not= ex-type (type e)))
           {:type  :fail :actual (type e) :expected ex-type
            :extra "exception did not match type"}

           (and regex (not (re-find regex e-msg)))
           {:type  :fail :actual e-msg :expected (str regex)
            :extra "exception's message did not match regex"}

           (and fn (not (fn e)))
           {:type  :fail :actual e :expected fn-pr
            :extra "checker function failed"}

           :else {:type :pass :actual "act" :expected "exp"})
      (merge {:message     msg
              :assert-type 'throws?
              :throwable   e}))))

(defn check-error [msg e criteria & [fn-pr]]
  (apply check-error* msg e
    ((juxt :ex-type :regex :fn :fn-pr)
     (assoc criteria :fn-pr fn-pr))))

(defn assert-expr [msg [disp-key & form]]
  (cond
    (= '= disp-key) (eq-assert-expr msg form)
    (= 'exec disp-key) (fn-assert-expr msg form)
    :else {:type     :fail :message msg :actual disp-key
           :expected #{"exec" "eq" "throws?"}}))

(defn triple->assertion [cljs? {:keys [actual arrow expected]}]
  (let [prefix (if cljs? "cljs.test" "clojure.test")
        is     (symbol prefix "is")
        msg    (str actual " " arrow " " expected)]
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
      (let [cls (if cljs? :default Throwable)]
        (if (instance? java.util.regex.Pattern expected)
          `(~is (~'thrown-with-msg? ~cls ~expected ~actual)
             ~msg)
          `(~is (~'thrown? ~expected ~actual)
             ~msg)))

      (throw (ex-info "invalid arrow" {:arrow arrow})))))

(defn fix-conform [conformed-assertions]
  ;;see issue: #31
  (if (vector? (second conformed-assertions))
    (vec (cons (first conformed-assertions) (second conformed-assertions)))
    conformed-assertions))

(defn block->asserts [cljs? {:keys [behavior triples]}]
  (let [asserts (map (partial triple->assertion cljs?) triples)]
    `(im/with-reporting ~{:type :behavior :string (if (empty? behavior) "unmarked" behavior)}
       ~@asserts)))

#?(:clj
   (do
     (defmethod cljs.test/assert-expr '= [env msg form]
       `(cljs.test/do-report ~(assert-expr msg form)))
     (defmethod cljs.test/assert-expr 'exec [env msg form]
       `(cljs.test/do-report ~(assert-expr msg form)))
     (defmethod clojure.test/assert-expr '= [msg form]
       `(clojure.test/do-report ~(assert-expr msg form)))
     (defmethod clojure.test/assert-expr 'exec [msg form]
       `(clojure.test/do-report ~(assert-expr msg form)))))

