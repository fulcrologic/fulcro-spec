(ns untangled-spec.assert-expr)

(defn fn-assert-expr [msg [f arg :as form]]
  `(let [result# (~f ~arg)]
     {:type (if result# :pass :fail) :message ~msg
      :actual ~arg :expected '~f}))

(defn assert-expr [disp-key msg form]
  (case (str disp-key)
    "call" (fn-assert-expr msg (rest form))
    :else {:type :fail :message "ELSE" :actual "BAD" :expected ""}))
