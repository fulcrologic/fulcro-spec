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
    `(~'do-report {:type :fail
                              :message ~msg
                              :extra ~extra})

    :else
    `(let [value# ~form]
       (~'do-report {:type (if value# :pass :fail)
                                :message ~msg, :expected '~form
                                :actual value#, :extra ~extra})
       value#)))

#?(:clj
    (defmacro untangled-is [form & [msg extra]]
      `(try ~(assert-expr form msg extra)
            (catch #?(:clj Throwable
                      :cljs js/Object) t#
              (~'do-report {:type :error, :message ~msg
                                       :expected '~form, :actual t#
                                       :extra ~extra})))))

(defn handle-exception [e] e)

(defn ->msg [l a r] (str l " " a " " r))

;TODO: try adding meta instead of using :extra
(defn triple->assertion [[left arrow expected]]
  (case arrow
    =>
    (let [actual left]
      `(let [actual# (try ~actual (catch #?(:clj Exception
                                            :cljs js/Object) ~'e
                                    (handle-exception ~'e)))
             expected# ~expected]
         (untangled-is (= actual# expected#)
                       (->msg '~actual '~arrow ~expected)
                       {:arrow '~arrow
                        :actual   actual#
                        :expected expected#})))

    =is=>
    `(~'is (~@expected ~left)
           (->msg '~left '~arrow '~expected))

    =fn=>
    (let [checker expected
          arg left]
      `(let [arg# (try ~arg (catch #?(:clj Exception
                                      :cljs js/Object) ~'e
                              (handle-exception ~'e)))]
         (untangled-is (~checker arg#)
                       (->msg '~arg '~arrow '~checker)
                       {:arrow '~arrow
                        :actual   arg#
                        :expected '~checker})))

    =throws=>
    (let [should-throw left
          criteria expected]
      `(untangled-is (try ~should-throw
                          (throw (ex-info
                                   (str "Expected an '"
                                        (first '~criteria)
                                        "' to be thrown!")
                                   {::type ::internal}))
                          (catch #?(:clj Throwable
                                    :cljs js/Object) ~'e
                            (exception-matches? ~'e ~@criteria)))
                     (->msg '~should-throw '~arrow '~criteria)
                     {:arrow '~arrow
                      :expected '~criteria}))

    (throw (ex-info "invalid arrow" {:arrow arrow}))))
