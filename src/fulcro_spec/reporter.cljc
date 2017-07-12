(ns fulcro-spec.reporter
  #?(:cljs (:require-macros [fulcro-spec.reporter]))
  (:require
    #?@(:cljs ([cljs-uuid-utils.core :as uuid]
               [cljs.stacktrace :refer [parse-stacktrace]]))
    [clojure.set :as set]
    [clojure.test :as t]
    [com.stuartsierra.component :as cp]
    [fulcro-spec.diff :refer [diff]])
  #?(:clj
     (:import
       (java.text SimpleDateFormat)
       (java.util Date UUID))
     :cljs
     (:import
       (goog.date Date))))

(defn new-uuid []
  #?(:clj (UUID/randomUUID)
     :cljs (uuid/uuid-string (uuid/make-random-uuid))))

(defn fix-str [s]
  (case s
    "" "\"\""
    nil "nil"
    s))

(defn now-time []
  #?(:clj (System/currentTimeMillis) :cljs (js/Date.now)))

(defn make-testreport
  ([] (make-testreport []))
  ([initial-items]
   {:id (new-uuid)
    :namespaces []
    :start-time (now-time)
    :test 0 :pass 0
    :fail 0 :error 0}))

(defn make-testitem
  [{:keys [string form-meta]}]
  (cond-> {:id (new-uuid)
           :name string
           :status {}
           :test-items []
           :test-results []}
    form-meta (assoc :form-meta form-meta)))

(defn make-manual [test-name] (make-testitem {:string (str test-name " (MANUAL TEST)")}))

#?(:cljs (defn- stack->trace [st] (parse-stacktrace {} st {} {})))

(defn merge-in-diff-results
  [{:keys [actual expected assert-type] :as test-result}]
  (cond-> test-result (#{'eq} assert-type)
    (assoc :diff (diff expected actual))))

(defn make-test-result
  [status t]
  (-> t
    (merge {:id (new-uuid)
            :status status
            :where (t/testing-vars-str t)})
      (merge-in-diff-results)
      #?(:cljs (#(if (some-> % :actual .-stack)
                   (assoc % :stack (-> % :actual .-stack stack->trace))
                   %)))
      (update :actual fix-str)
      (update :expected fix-str)))

(defn make-tests-by-namespace
  [test-name]
  {:id (new-uuid)
   :name       test-name
   :test-items []
   :status     {}})

(defn set-test-result [{:keys [state path]} status]
  (let [all-paths (sequence
                    (comp (take-while seq) (map vec))
                    (iterate (partial drop-last 2) @path))]
    (swap! state
      (fn [state]
        (reduce (fn [state path]
                  (update-in state
                    (conj path :status status)
                    (fnil inc 0)))
          state all-paths)))))

(defn begin* [{:keys [state path]} t]
  (let [path @path
        test-item (make-testitem t)
        test-items-count (count (get-in @state (conj path :test-items)))]
    (swap! state assoc-in
      (conj path :test-items test-items-count)
      test-item)
    [test-item test-items-count]))

(defn get-namespace-location [namespaces nsname]
  (let [namespace-index
        (first (keep-indexed (fn [idx val]
                               (when (= (:name val) nsname)
                                 idx))
                 namespaces))]
    (or namespace-index
      (count namespaces))))

(defn inc-report-counter [type]
  (#?(:clj t/inc-report-counter :cljs t/inc-report-counter!) type))

(defn failure* [{:as this :keys [state path]} t failure-type]
  (inc-report-counter failure-type)
  (let [path @path
        {:keys [test-results]} (get-in @state path)
        new-result (make-test-result failure-type t)]
    (set-test-result this failure-type)
    (swap! state update-in (conj path :test-results)
      conj new-result)
    new-result))

(defn error [this t]
  (failure* this t :error))

(defn fail [this t]
  (failure* this t :fail))

(defn pass [this t]
  (inc-report-counter :pass)
  (set-test-result this :pass))

(defn push-test-item-path [{:keys [path]} test-item index]
  (swap! path conj :test-items index))

(defn pop-test-item-path [{:keys [path]}]
  (swap! path (comp pop pop)))

(defn begin-namespace [{:keys [state path]} t]
  (let [test-name (ns-name (:ns t))
        namespaces (get-in @state (conj @path :namespaces))
        name-space-location (get-namespace-location namespaces test-name)]
    (swap! path conj :namespaces name-space-location)
    (swap! state assoc-in @path
      (make-tests-by-namespace test-name))))

(defn end-namespace [this t] (pop-test-item-path this))

(defn begin-specification [this t]
  (apply push-test-item-path this
    (begin* this t)))

(defn end-specification [this t] (pop-test-item-path this))

(defn begin-behavior [this t]
  (apply push-test-item-path this
    (begin* this t)))

(defn end-behavior [this t] (pop-test-item-path this))

(defn begin-manual [this t]
  (apply push-test-item-path this
    (begin* this t)))

(defn end-manual [this t]
  (set-test-result this :manual)
  (pop-test-item-path this))

(defn begin-provided [this t]
  (apply push-test-item-path this
    (begin* this t)))

(defn end-provided [this t] (pop-test-item-path this))

(defn summary [{:keys [state]} t]
  (let [end-time (now-time)
        end-date (.getTime (new Date))]
    (swap! state
      (fn [{:as st :keys [start-time]}]
        (-> st
          (assoc :end-time end-date)
          (assoc :run-time (- end-time start-time))))))
  (swap! state merge t))

(defn reset-test-report! [{:keys [state path]}]
  (reset! state (make-testreport))
  (reset! path []))

(defrecord TestReporter [state path]
  cp/Lifecycle
  (start [this] this)
  (stop [this]
    (reset-test-report! this)
    this))

(defn make-test-reporter
  "Just a shell to contain minimum state necessary for reporting"
  []
  (map->TestReporter
    {:state (atom (make-testreport))
     :path (atom [])}))

(defn get-test-report [reporter]
  @(:state reporter))

(defn fulcro-report [{:keys [test/reporter] :as system} on-complete]
  (fn [t]
    (case (:type t)
      :pass (pass reporter t)
      :error (error reporter t)
      :fail (fail reporter t)
      :begin-test-ns (begin-namespace reporter t)
      :end-test-ns (end-namespace reporter t)
      :begin-specification (begin-specification reporter t)
      :end-specification (end-specification reporter t)
      :begin-behavior (begin-behavior reporter t)
      :end-behavior (end-behavior reporter t)
      :begin-manual (begin-manual reporter t)
      :end-manual (end-manual reporter t)
      :begin-provided (begin-provided reporter t)
      :end-provided (end-provided reporter t)
      :summary (do (summary reporter t) #?(:clj (on-complete system)))
      #?@(:cljs [:end-run-tests (on-complete system)])
      nil)))

#?(:clj
   (defmacro with-fulcro-reporting [system on-complete & body]
     `(binding [t/report (fulcro-report ~system ~on-complete)] ~@body)))
