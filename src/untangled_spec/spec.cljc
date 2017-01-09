(ns untangled-spec.spec
  (:require
    [clojure.spec :as s]))

(defn conform! [spec x]
  (let [rt (s/conform spec x)]
    (when (s/invalid? rt)
      (throw (ex-info (s/explain-str spec x)
               (s/explain-data spec x))))
    rt))

(s/def ::any (constantly true))

(defn regex? [x] (= (type x) (type #"")))
(s/def ::regex regex?)
