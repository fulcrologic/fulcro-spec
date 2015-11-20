(ns untangled-spec.assertions)

(defn exception-matches? [& [e exp-type re f]]
  (when (some-> (ex-data e) ::type
                (= ::internal))
    (throw e))
  (and (or (= exp-type (type e))
           (throw (ex-info "exception did not match type"
                           {:e-type (type e) :expected-type exp-type
                            :e (.toString e)})))
       (or (re-find (or re #"") (.getMessage e))
           (throw (ex-info "exception's message did not match regex"
                           {:regex re :msg (.getMessage e)})))
       (or ((or f (fn [_] true)) e)
           (throw e))))

(defn assert-expr [form msg extra]
  (cond
    (nil? form)
    `(clojure.test/do-report {:type :fail
                              :message ~msg
                              :extra ~extra})

    :else
    `(let [value# ~form]
       (clojure.test/do-report {:type (if value# :pass :fail)
                                :message ~msg, :expected '~form
                                :actual value#, :extra ~extra})
       value#)))

;TODO: dont use 'is' as the name
(defmacro is [form & [msg extra]]
  `(try ~(assert-expr form msg extra)
        (catch Throwable t#
          (clojure.test/do-report {:type :error, :message ~msg
                                   :expected '~form, :actual t#
                                   :extra ~extra}))))

;TODO: try adding meta instead of using :extra
(defn triple->assertion [[left arrow expected]]
  (case arrow
    =>
    (let [actual left]
      `(let [actual# (try ~actual
                          (catch Exception ~'e
                            ~'e))
             expected# ~expected]
         (is (= actual# expected#)
             (format "%s %s %s"
                     '~actual '~arrow ~expected)
             {:arrow '~arrow
              :actual   actual#
              :expected expected#})))

    =fn=>
    (let [checker expected
          arg left]
      `(let [arg# ~arg]
         (is (~checker arg#)
             (format "%s %s %s"
                     '~arg '~arrow '~checker)
             {:arrow '~arrow
              :actual   arg#
              :expected '~checker})))

    =throws=>
    (let [should-throw left
          criteria expected]
      `(is (try ~should-throw
                (throw (ex-info
                         (str "Expected an '"
                              (first '~criteria)
                              "' to be thrown!")
                         {::type ::internal}))
                (catch Exception ~'e
                  (exception-matches? ~'e ~@criteria)))
           (format "%s %s %s"
                   '~should-throw '~arrow '~criteria)
           {:arrow '~arrow
            :expected '~criteria}))

    (throw (ex-info "invalid arrow" {:arrow arrow}))))
