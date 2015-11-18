(ns untangled-spec.assertions)

(defn exception-matches? [& [e exp-type re f]]
  (and (or (= exp-type (type e))
           (throw (ex-info "exception did not match type"
                           {:e-type (type e) :expected-type exp-type
                            :e (.toString e)})))
       (or (re-find (or re #"") (.getMessage e))
           (throw (ex-info "exception's message did not match regex"
                           {:regex re :msg (.getMessage e)})))
       (or ((or f (fn [_] true)) e)
           (throw e))))

(defn triple->assertion [[left arrow expected]]
  (case arrow
    =fn=>
    (let [checker expected
          arg left]
      `(~'is (~checker ~arg)
             (format "%s %s %s"
                     '~arg '~arrow '~checker)))

    =throws=>
    (let [should-throw left
          criteria expected]
      `(~'is (try ~should-throw
                  (catch Exception ~'e
                    (exception-matches? ~'e ~@criteria)))
             (format "%s %s %s"
                     '~should-throw '~arrow '~criteria)))

    =>
    (let [actual left]
      `(~'is (= ~actual ~expected)
             (format "%s %s %s"
                     '~actual '~arrow ~expected)))

    (throw (ex-info "invalid arrow" {:arrow arrow}))))
