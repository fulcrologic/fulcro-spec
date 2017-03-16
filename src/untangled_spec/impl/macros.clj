(ns untangled-spec.impl.macros)

(defn- cljs-env?
  "https://github.com/Prismatic/schema/blob/master/src/clj/schema/macros.clj"
  [env] (boolean (:ns env)))

(defmacro with-reporting
  "Wraps body in a begin-* and an end-* do-report if the msg contains a :type"
  [msg & body]
  (let [cljs? (cljs-env? &env)
        do-report (symbol (if cljs? "cljs.test" "clojure.test") "do-report")
        make-msg (fn [msg-loc]
                   (update msg :type
                     #(keyword (str msg-loc "-" (name %)))))]
    (if-not (:type msg) `(do ~@body)
      `(do
         (~do-report ~(make-msg "begin"))
         ~@body
         (~do-report ~(make-msg "end"))))))
