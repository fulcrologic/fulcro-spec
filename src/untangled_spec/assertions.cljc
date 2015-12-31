(ns untangled-spec.assertions)

(defn handle-exception [e] e)

(defn ->msg [l a r] (str l " " a " " r))

(defn triple->assertion [cljs? [left arrow expected]]
  (let [prefix (if cljs? "cljs.test" "clojure.test")
        is (symbol prefix "is")]
    (case arrow
      =>
      (let [actual left]
        `(~is (= ~actual ~expected)
              (->msg '~actual '~arrow ~expected)))

      =fn=>
      (let [checker expected
            arg left]
        `(~is (~'call ~checker ~arg)
              (->msg '~arg '~arrow '~checker)))

      =throws=>
      (let [should-throw left
            criteria expected]
        `(~is (~'throws? ~cljs? ~should-throw ~@criteria)
              (->msg '~should-throw '~arrow '~criteria)))

      (throw (ex-info "invalid arrow" {:arrow arrow})))))
