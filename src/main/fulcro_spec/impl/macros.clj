(ns fulcro-spec.impl.macros)

(defn cljs-env?
  "https://github.com/Prismatic/schema/blob/master/src/clj/schema/macros.clj"
  [env] (boolean (:ns env)))

(defn if-cljs [env cljs clj]
  (if (cljs-env? env) cljs clj))

(defmacro try-report [block & body]
  (let [prefix    (if-cljs &env "cljs.test" "clojure.test")
        do-report (symbol prefix "do-report")]
    `(try ~@body
          (catch ~(if-cljs &env (symbol "js" "Object") (symbol "Throwable"))
                 e#
            (~do-report {:type    :error :actual e#
                         :message ~block :expected "IT TO NOT THROW!"})))))

(defn make-msg [msg-loc msg]
  (update msg :type
    #(keyword (str msg-loc "-" (name %)))))

(defmacro begin-reporting
  [msg & body]
  (let [cljs?     (cljs-env? &env)
        do-report (symbol (if cljs? "cljs.test" "clojure.test") "do-report")]
    `(~do-report ~(make-msg "begin" msg))))

(defmacro end-reporting
  [msg & body]
  (let [cljs?     (cljs-env? &env)
        do-report (symbol (if cljs? "cljs.test" "clojure.test") "do-report")]
    `(~do-report ~(make-msg "end" msg))))

(defmacro with-reporting
  "Wraps body in a begin-* and an end-* do-report if the msg contains a :type"
  [msg & body]
  (let [cljs?     (cljs-env? &env)
        do-report (symbol (if cljs? "cljs.test" "clojure.test") "do-report")]
    (if-not (:type msg)
      `(do ~@body)
      `(do
         (~do-report ~(make-msg "begin" msg))
         ~@body
         (~do-report ~(make-msg "end" msg))))))
