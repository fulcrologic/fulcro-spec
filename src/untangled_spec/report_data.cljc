(ns untangled-spec.report-data)

(defn make-testreport
  ([] (make-testreport []))
  ([initial-items]
   {
    :summary    ""
    :namespaces []
    :tested     0
    :passed     0
    :failed     0
    :error      0
    }
    )
  )

(defn make-testitem
  [name]
  {
   :name         name
   :status       :pending
   :test-items   []
   :test-results []
   }
  )

(defn make-test-result
  [result result-detail]
  {:status     result
   :message    (:message result-detail)
   :where      (:where result-detail)
   :expected   (:expected result-detail)
   :actual     (:actual result-detail)
   :raw-actual (:raw-actual result-detail)
   :extra      (:extra result-detail)
   :arrow      (:arrow result-detail)
   })

(defn make-tests-by-namespace
  [name]
  {
   :name       name
   :test-items []
   :status     :pending
   })

(def ^:dynamic *test-state* (atom (make-testreport)))
(def ^:dynamic *test-scope* (atom []))

(defn push-test-scope [test-item index] (swap! *test-scope* #(conj % :test-items index)))

(defn pop-test-scope [] (swap! *test-scope* #(-> % (pop) (pop))))

(defn set-test-result [status]
  (loop [current-test-result-path @*test-scope*]
    (if (> (count current-test-result-path) 1)
      (let [target (get-in @*test-state* current-test-result-path)
            current-status (:status target)]
        (if (not (or (= current-status :error) (= current-status :failed)))
          (swap! *test-state* #(assoc-in % (concat current-test-result-path [:status]) status)))
        (recur (drop-last 2 current-test-result-path))))))

(defn begin-namespace [name]
  (let [namespaces (get @*test-state* :namespaces)
        namespace-index (first (keep-indexed (fn [idx val] (when (= (:name val) name) idx)) namespaces))
        name-space-location (if namespace-index namespace-index (count namespaces))
        ]
    (reset! *test-scope* [:namespaces name-space-location])
    (swap! *test-state* #(assoc-in % [:namespaces name-space-location] (make-tests-by-namespace name))))
  )

(defn end-namespace []
  (pop-test-scope)
  )

(defn begin-specification [spec]
  (let [test-item (make-testitem spec)
        test-items-count (count (get-in @*test-state* (concat @*test-scope* [:test-items])))]
    (swap! *test-state* #(assoc-in % (concat @*test-scope* [:test-items test-items-count]) test-item))
    (push-test-scope test-item test-items-count)
    )
  )

(defn end-specification [] (pop-test-scope))


(defn begin-behavior [behavior]
  (let [test-item (make-testitem behavior)
        parent-test-item (get-in @*test-state* @*test-scope*)
        test-items-count (count (:test-items parent-test-item))]
    (swap! *test-state* #(assoc-in % (concat @*test-scope* [:test-items test-items-count]) test-item))
    (push-test-scope test-item test-items-count)
    )
  )

(defn end-behavior [] (pop-test-scope))


(defn begin-provided [provided]
  (let [test-item (make-testitem provided)
        test-items-count (count (get-in @*test-state* (concat @*test-scope* [:test-items])))]
    (swap! *test-state* #(assoc-in % (concat @*test-scope* [:test-items test-items-count]) test-item))
    (push-test-scope test-item test-items-count)
    )
  )

(defn end-provided [] (pop-test-scope))

(defn pass [] (set-test-result :passed))

(defn error [detail]
  (let [translated-item-path @*test-scope*
        current-test-item (get-in @*test-state* translated-item-path)
        test-result (make-test-result :error detail)
        test-result-path (concat translated-item-path
                                 [:test-results (count (:test-results current-test-item))])]
    (set-test-result :error)
    (swap! *test-state* #(assoc-in % test-result-path test-result))
    ))

(defn fail [detail]
  (let [translated-item-path @*test-scope*
        current-test-item (get-in @*test-state* translated-item-path)
        test-result (make-test-result :failed detail)
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

