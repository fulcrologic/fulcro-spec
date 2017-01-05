(ns untangled-spec.reporters.terminal-spec
  (:require
    [untangled-spec.core
     :refer [specification component behavior provided assertions]]
    [untangled-spec.reporters.terminal :as rt]
    [clojure.test :as t]))

(defn stop? [e] (-> e ex-data :untangled-spec.reporters.terminal/stop?))

(specification "parse-message"
  (behavior "can parse a message with:"
    (assertions
      "arrows in actual or expected"
      (rt/parse-message (str "12 =fn=> (fn [x] (do (assertions x => 23) true))"))
      => {:arrow "=fn=>"
          :actual "12"
          :expected "(fn [x] (do (assertions x => 23) true))"}
      (rt/parse-message (str "(arrow-is-okay \"=>\") => exp"))
      => {:arrow "=>"
          :actual "(arrow-is-okay \"=>\")"
          :expected "exp"}

      (rt/parse-message (str "#inst \"2017-01-05T02:25:12.737-00:00\" => 32"))
      =fn=> #(assertions
               % => {:actual "Wed Jan 04 18:25:12 PST 2017"
                     :arrow "=>"
                     :expected "32"}))))

(specification "print-test-result"
  (provided "if (isa? actual Throwable) & (= status :error), it should print-throwable"
    (rt/print-throwable _) => _
    (rt/env :quick-fail?) => true
    (let [e (ex-info "howdy" {})]
      (assertions
        (rt/print-test-result {:status :error :actual e} (constantly nil) 0)
        =throws=> (clojure.lang.ExceptionInfo #"" stop?)))))

(def big-thing (zipmap (range 5)
                 (repeat (zipmap (range 5) (range)))))

(specification "pretty-str"
  (behavior "put newlines in between lines"
    (assertions
      (rt/pretty-str big-thing 1) => (str "{0 {0 0, 1 1, 2 2, 3 3, 4 4},\n"
                                       "    1 {0 0, 1 1, 2 2, 3 3, 4 4},\n"
                                       "    2 {0 0, 1 1, 2 2, 3 3, 4 4},\n"
                                       "    3 {0 0, 1 1, 2 2, 3 3, 4 4},\n"
                                       "    4 {0 0, 1 1, 2 2, 3 3, 4 4}}"))))
