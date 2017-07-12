(ns fulcro-spec.impl.macros
  (:require
    [fulcro-spec.selectors :as sel]))

(defn cljs-env?
  "https://github.com/Prismatic/schema/blob/master/src/clj/schema/macros.clj"
  [env] (boolean (:ns env)))

(defn if-cljs [env cljs clj]
  (if (cljs-env? env) cljs clj))

(defmacro try-report [block & body]
  (let [prefix (if-cljs &env "cljs.test" "clojure.test")
        do-report (symbol prefix "do-report")]
    `(try ~@body
       (catch ~(if-cljs &env (symbol "js" "Object") (symbol "Throwable"))
         e# (~do-report {:type :error :actual e#
                         :message ~block :expected "IT TO NOT THROW!"})))))

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

(defmacro when-selected-for
  "Only runs body if it is selectors for based on the passed in selectors.
   See fulcro-spec.selectors for more info."
  [selectors & body]
  `(when (sel/selected-for? ~selectors)
     ~@body))
