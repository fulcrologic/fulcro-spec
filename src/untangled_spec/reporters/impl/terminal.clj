(ns untangled-spec.reporters.impl.terminal
  (:require
    [untangled-spec.reporters.impl.base-reporter :as impl]))

(def ^:dynamic *test-state* (atom (impl/make-testreport)))
(def ^:dynamic *test-scope* (atom []))

(defn push-test-scope [test-item index] (swap! *test-scope* #(conj % :test-items index)))

(defn pop-test-scope [] (swap! *test-scope* #(-> % (pop) (pop))))

(defn set-test-result [status]
  (impl/set-test-result *test-state* @*test-scope* status))

(defn begin-namespace [name]
  (let [namespaces (get @*test-state* :namespaces)
        namespace-index (first (keep-indexed (fn [idx val] (when (= (:name val) name) idx)) namespaces))
        name-space-location (if namespace-index namespace-index (count namespaces))
        ]
    (reset! *test-scope* [:namespaces name-space-location])
    (swap! *test-state* #(assoc-in % [:namespaces name-space-location] (impl/make-tests-by-namespace name))))
  )

(defn end-namespace []
  (pop-test-scope)
  )

(defn begin-specification [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-specification [] (pop-test-scope))

(defn begin-behavior [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-behavior [] (pop-test-scope))

(defn begin-provided [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-provided [] (pop-test-scope))

(defn pass [] (set-test-result :passed))

(defn error [detail]
  (let [translated-item-path @*test-scope*
        current-test-item (get-in @*test-state* translated-item-path)
        test-result (impl/make-test-result :error detail)
        test-result-path (concat translated-item-path
                                 [:test-results (count (:test-results current-test-item))])]
    (set-test-result :error)
    (swap! *test-state* #(assoc-in % test-result-path test-result))
    ))

(defn fail [detail]
  (let [translated-item-path @*test-scope*
        current-test-item (get-in @*test-state* translated-item-path)
        test-result (impl/make-test-result :failed detail)
        test-result-path (concat translated-item-path
                                 [:test-results (count (:test-results current-test-item))])]
    (set-test-result :failed)
    (swap! *test-state* #(assoc-in % test-result-path test-result))
    ))

(defn summary [stats]
  (let [translated-item-path @*test-scope*]
    (swap! *test-state* #(assoc-in % (concat translated-item-path [:tested]) (:tested stats)))
    (swap! *test-state* #(assoc-in % (concat translated-item-path [:passed]) (:passed stats)))
    (swap! *test-state* #(assoc-in % (concat translated-item-path [:failed]) (:failed stats)))
    (swap! *test-state* #(assoc-in % (concat translated-item-path [:error]) (:error stats)))
    ))
