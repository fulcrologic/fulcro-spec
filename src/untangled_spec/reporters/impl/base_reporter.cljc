(ns untangled-spec.reporters.impl.base-reporter
  (:require
    #?@(:cljs ([cljs-uuid-utils.core :as uuid]
               [cljs.stacktrace :refer [parse-stacktrace]]))
    [clojure.set :as set]
    [#?(:clj clojure.test :cljs cljs.test) :as t]
    [untangled-spec.reporters.impl.diff :refer [diff]]))

(defn fix-str [s]
  (case s
    "" "\"\""
    nil "nil"
    s))

(defn make-testreport
  ([] (make-testreport []))
  ([initial-items]
   {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
    :summary       ""
    :namespaces    []
    #?@(:clj [:tested 0])
    :passed        0
    :failed        0
    :error         0}))

(defn make-testitem
  [test-name]
  {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
   :name         test-name
   :status       {}
   :test-items   []
   :test-results []})

(defn make-manual [test-name] (make-testitem (str test-name " (MANUAL TEST)")))

#?(:cljs (defn- stack->trace [st] (parse-stacktrace {} st {} {})))

(defn merge-in-diff-results
  [{:keys [actual expected assert-type] :as test-result}]
  (cond-> test-result (#{'eq} assert-type)
    (assoc :diff (diff expected actual))))

(defn make-test-result
  [status t]
  (-> t
    (merge {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
            :status status
            :where (t/testing-vars-str t)})
      (merge-in-diff-results)
      #?(:cljs (#(if (some-> % :actual .-stack)
                   (assoc % :stack (-> % :actual .-stack stack->trace))
                   %)))))

(defn make-tests-by-namespace
  [test-name]
  {#?@(:cljs [:id (uuid/uuid-string (uuid/make-random-uuid))])
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
        test-item (make-testitem (:string t))
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

(defn failure* [{:as this :keys [state path]} t failure-type]
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
  (failure* this t :failed))

(defn pass [this t] (set-test-result this :passed))

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
  (swap! state merge
    (set/rename-keys t
      {:pass :passed
       :fail :failed
       :test :tested})))
