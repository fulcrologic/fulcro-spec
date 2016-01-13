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

(defn filter-class [{:keys [report/filter status]}]
  (when (or (and (#{:failed} filter)
                 (not (#{:error :failed} status)))
            (and (=    :manual filter)
                 (not= :manual status)))
    "hidden"))

(defn stack->trace [st] (parse-stacktrace {} st {} {}))

(defn ?ø [x]
  (if (= x "ø")
    '*nil* x))
