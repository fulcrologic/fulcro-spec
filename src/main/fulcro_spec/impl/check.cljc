(ns fulcro-spec.impl.check
  #?(:cljs (:require
             [goog.string.format]
             [goog.string :as gstring])))

(defn format-str [& args]
  (apply #?(:cljs gstring/format :clj format) args))

(defn prepend-message [msg fail]
  (if-not (:message fail)
    (assoc fail :message msg)
    (update fail :message (partial str msg "\n"))))

(defn append-message [message failure]
  (update failure :message
    (fn [msg]
      (if-not msg message
        (str msg "\n" message)))))

(defn failure->report [test-msg failure actual]
  (merge {:type :fail :fulcro-spec.check/actual actual}
    (cond->> (prepend-message test-msg failure)
      (::path failure)
      (append-message
        (str "at path " (::path failure) ":")))))

(defn check-expr [cljs? msg [_ checker actual]]
  (let [prefix (if cljs? "cljs.test" "clojure.test")
        do-report (symbol prefix "do-report")]
    `(let [checker# ~checker, actual# ~actual, msg# ~msg
           location# ~(select-keys (meta checker) [:line])]
       (if-let [failures# ((fulcro-spec.check/all* checker#) actual#)]
         (doseq [f# failures#]
           (~do-report (merge (failure->report msg# f# actual#) location#)))
         (~do-report (merge {:type :pass :message msg#} location#))))))
