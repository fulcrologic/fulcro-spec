(ns untangled-spec.assert-expr)

(defn fn-assert-expr [msg [f arg :as form]]
  `(let [arg# ~arg
         result# (~f arg#)]
     {:type (if result# :pass :fail) :message ~msg
      :actual arg# :expected '~f}))

(defn eq-assert-expr [msg [act exp]]
  `(let [act# ~act
         exp# ~exp
         result# (= act# exp#)]
     {:type (if result# :pass :fail) :message ~msg
      :actual act# :expected exp#}))

(defn exception-matches? [msg e exp-type & [re f]]
  (cond
    (some-> (ex-data e) :type (= ::internal))
    {:type :error :message (.getMessage e)
     :actual e :expected "it to throw"}

    (not= exp-type (type e))
    {:type :fail :message "exception did not match type"
     :actual (type e) :expected exp-type}

    (and re (not (re-find re (.getMessage e))))
    {:type :fail :message "exception's message did not match regex"
     :actual (.getMessage e) :expected (str re)}

    (and f (not (f e)))
    {:type :fail :message "checker function failed"
     :actual e :expected f}

    :else
    {:type :passed :message msg
     :actual "act" :expected "exp"}))

(defn throws-assert-expr [msg [cljs? should-throw & criteria]]
  `(try ~should-throw
        (throw (ex-info (str "Expected an '"
                             (first '~criteria)
                             "' to be thrown!")
                        {:type ::internal}))
        (catch ~(if (not cljs?) (symbol "Throwable") (symbol "js" "Object"))
          e# (exception-matches? ~msg e# ~@criteria))))

(defn assert-expr [disp-key msg form]
  (case (str disp-key)
    "call"    (fn-assert-expr     msg (rest form))
    "eq"      (eq-assert-expr     msg (rest form))
    "throws?" (throws-assert-expr msg (rest form))
    :else {:type :fail :message "ELSE" :actual "BAD" :expected ""}))
