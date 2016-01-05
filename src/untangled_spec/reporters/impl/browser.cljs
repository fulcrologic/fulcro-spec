(ns ^:figwheel-always untangled-spec.reporters.impl.browser
  (:require
    [cljs.stacktrace :refer [parse-stacktrace]]))

(defn itemclass [status]
  (str "test-" (name status)))

(defn color-favicon-data-url [color]
  (let [cvs (.createElement js/document "canvas")]
    (set! (.-width cvs) 16)
    (set! (.-height cvs) 16)
    (let [ctx (.getContext cvs "2d")]
      (set! (.-fillStyle ctx) color)
      (.fillRect ctx 0 0 16 16))
    (.toDataURL cvs)))

(defn change-favicon-to-color [color]
  (let [icon (.getElementById js/document "favicon")]
    (set! (.-href icon) (color-favicon-data-url color))))

(defn filter-class [test-item]
  (let [filter (:report/filter test-item)
        state (:status test-item)]
    (cond
      (and (= :failed filter) (not= :error state) (not= :failed state)) "hidden"
      (and (= :manual filter) (not= :manual state)) "hidden"
      (= :all filter) "")))

(defn stack->trace [st] (parse-stacktrace {} st {} {}))
