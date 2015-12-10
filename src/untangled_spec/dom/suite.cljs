(ns ^:figwheel-always untangled-spec.dom.suite
  (:require
    goog.object
    [om.dom :as dom]
    [goog.dom :as gdom]
    [om.next :as om :refer-macros [defui]]
    [cljs-uuid-utils.core :as uuid]
    [cljs.stacktrace :refer [parse-stacktrace]]))

(enable-console-print!)

(defprotocol ITest
  (render-tests [this] "Render the test results to the DOM")
  (set-test-result [this status] "Set the pass/fail/error result of a test")
  (pass [this] "Tests are reporting that they passed")
  (fail [this detail] "Tests are reporting that they failed, with additional details")
  (error [this detail] "Tests are reporting that they error'ed, with additional details")
  (summary [this stats] "A summary of the test run, how many passed/failed/error'ed")
  (begin-manual [this behavior] "Manual test")
  (end-manual [this] "Manual test")
  (begin-behavior [this behavior] "Tests are reporting the start of a behavior")
  (end-behavior [this] "Tests are reporting the end of a behavior")
  (begin-provided [this behavior] "Tests are reporting the start of a provided")
  (end-provided [this] "Tests are reporting the end of a provided")
  (begin-specification [this spec] "Tests are reporting the start of a specification")
  (end-specification [this] "Tests are reporting the end of a specification")
  (begin-namespace [this name] "Tests are reporting the start of a namespace")
  (push-test-item-path [this test-item index] "Push a new test items onto the test item path")
  (pop-test-item-path [this] "Pops the last test item off of the test item path"))

(defn- find-first [pred coll] (first (filter pred coll)))

(defn checked-index [items index id-keyword value]
  (let [index-valid? (> (count items) index)
        proposed-item (if index-valid? (get items index) nil) ]
    (cond (and proposed-item
               (= value (get proposed-item id-keyword))) index
          :otherwise (->> (map-indexed vector items) (find-first #(= value (id-keyword (second %)))) (first)))))

(defn resolve-data-path [state path-seq]
  (reduce (fn [real-path path-ele]
            (if (sequential? path-ele)
              (do
                (if (not= 4 (count path-ele))
                  (js/console.log "ERROR: VECTOR BASED DATA ACCESS MUST HAVE A 4-TUPLE KEY")
                  (let [vector-key (first path-ele)
                        state-vector (get-in state (conj real-path vector-key))
                        lookup-function (second path-ele)
                        target-value (nth path-ele 2)
                        proposed-index (nth path-ele 3)
                        index (checked-index state-vector proposed-index lookup-function target-value) ]
                    (if index
                      (conj real-path vector-key index)
                      (do
                        (js/console.log "ERROR: NO ITEM FOUND AT DATA PATH")
                        (cljs.pprint/pprint path-seq)
                        real-path)))))
              (conj real-path path-ele)))
          [] path-seq))

(defn translate-item-path [app-state test-item-path]
  (loop [data (:top @app-state)
         path test-item-path
         result [:top]]
    (if (empty? path)
      result
      (let [resolved-path (resolve-data-path data (vector (seq (take 4 path))))
            context-data (get-in data resolved-path)]
        (recur context-data (drop 4 path) (concat result resolved-path))))))

(declare TestItem)

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


(defn make-testreport
  ([] (make-testreport []))
  ([initial-items]
   {:id            (uuid/uuid-string (uuid/make-random-uuid))
    :report/filter :all
    :summary       ""
    :namespaces    []
    :passed        0
    :failed        0
    :error         0}))


(defn make-testitem
  [name]
  {:id           (uuid/uuid-string (uuid/make-random-uuid))
   :name         name
   :status       :pending
   :test-items   []
   :test-results []})

(defn make-manual [name] (make-testitem (str name " (MANUAL TEST)")))

(defn make-test-result
  [result result-detail]
  {:id       (uuid/uuid-string (uuid/make-random-uuid))
   :status   result
   :message  (:message result-detail)
   :where    (:where result-detail)
   :expected (:expected result-detail)
   :actual   (:actual result-detail)})

(defn make-tests-by-namespace
  [name]
  {:id         (uuid/uuid-string (uuid/make-random-uuid))
   :name       name
   :test-items []
   :status     :pending})

(defn item-path [item index] [:test-items :id (:id item) index])

(defn result-path [item index] [:test-results :id (:id item) index])
(defn itemclass [status]
  (cond
    (= status :pending) "test-pending"
    (= status :manual) "test-manually"
    (= status :passed) "test-passed"
    (= status :error) "test-error"
    (= status :failed) "test-failed"))

(declare TestResult)

(defn filter-class [test-item]
  (let [filter (:report/filter test-item)
        state (:status test-item)]
    (cond
      (and (= :failed filter) (not= :error state) (not= :failed state)) "hidden"
      (and (= :manual filter) (not= :manual state)) "hidden"
      (= :all filter) "")))

(defn stack->trace [st] (parse-stacktrace {} st {} {}))

(defui TestSubResult
       Object
       (initLocalState [this] {:folded? true})
       (render [this]
               (let [{:keys [title value]} (om/props this)
                     {:keys [folded?]} (om/get-state this)]
                 (dom/tr nil
                         (dom/td #js {:className "test-result-title"} title)
                         (dom/td #js {:className "test-result"
                                      :onClick #(om/update-state! this update :folded? not)}
                                 (if (.-stack value)
                                   (dom/code #js {:className "stack-trace"}
                                             (if folded? \u25BA \u25BC)
                                             (str value)
                                             (dom/div #js {:className (if folded? "hidden" nil)}
                                                      (some-> value .-stack stack->trace)))
                                   (dom/code nil (str value))))))))

(def test-sub-result (om/factory TestSubResult))

(defui TestResult
       Object
       (render [this]
               (let [{:keys [message actual expected]} (om/props this)]
                 (dom/li nil
                         (dom/div nil
                                  (if message (dom/h3 nil message))
                                  (dom/table nil
                                             (dom/tbody nil
                                                        (test-sub-result {:title "Actual"
                                                                          :value actual})
                                                        (test-sub-result {:title "Expected"
                                                                          :value (or expected "")}))))))))

(def test-result (om/factory TestResult {:keyfn :id}))

(declare test-item)

(defui TestItem
       Object
       (render [this]
               (let [test-item-data (om/props this)
                     filter (:report/filter test-item-data) ]
                 (dom/li #js {:className "test-item "}
                         (dom/div #js {:className (filter-class test-item-data)}
                                  (dom/span #js {:className (itemclass (:status test-item-data))}
                                            (:name test-item-data))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv test-result
                                                (:test-results test-item-data)))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv (comp test-item #(assoc % :report/filter filter))
                                                (:test-items test-item-data))))))))

(def test-item (om/factory TestItem {:keyfn :id}))

(defui TestNamespace
       Object
       (initLocalState [this] {:folded? false})
       (render
         [this]
         (let [tests-by-namespace (om/props this)
               filter (:report/filter tests-by-namespace)
               {:keys [folded?]} (om/get-state this)]
           (dom/li #js {:className "test-item"}
                   (dom/div #js {:className "test-namespace"}
                            (dom/a #js {:href "#"
                                        :style #js {:textDecoration "none"} ;; TODO: refactor to css
                                        :onClick   #(om/update-state! this update :folded? not)}
                                   (dom/h2 #js {:className (itemclass (:status tests-by-namespace))}
                                           (if folded? \u25BA \u25BC)
                                           " Testing " (:name tests-by-namespace)))
                            (dom/ul #js {:className (if folded? "hidden" "test-list")}
                                    (mapv (comp test-item #(assoc % :report/filter filter))
                                          (:test-items tests-by-namespace))))))))

(def test-namespace (om/factory TestNamespace {:keyfn :name}))

(defui TestReport
       static om/IQuery
       (query [this] [:top :report/filter])
       Object
       (render [this]
               (let [props (om/props this)
                     test-report-data (-> props :top)
                     current-filter (-> props :report/filter)]
                 (dom/section #js {:className "test-report"}
                              ;TODO: move to defui filters
                              (dom/div #js {:name "filters" :className "filter-controls"}
                                       (dom/label #js {:htmlFor "filters"} "Filter: ")
                                       (dom/a #js {:className (if (= current-filter :all) "selected" "")
                                                   :onClick   #(om/transact! this '[(filter-all)])}
                                              "All")
                                       (dom/a #js {:className (if (= current-filter :manual) "selected" "")
                                                   :onClick   #(om/transact! this '[(filter-manual)])}
                                              "Manual")
                                       (dom/a #js {:className (if (= current-filter :failed) "selected" "")
                                                   :onClick   #(om/transact! this '[(filter-failed)])}
                                              "Failed"))
                              (dom/ul #js {:className "test-list"}
                                      (mapv (comp test-namespace
                                                  #(assoc % :report/filter current-filter))
                                            (:namespaces test-report-data)))
                              ;TODO: move to defui test-count
                              (let [rollup-stats (reduce (fn [acc item]
                                                           (let [counts [(:passed item) (:failed item) (:error item)
                                                                         (+ (:passed item) (:failed item) (:error item))]]
                                                             (map + acc counts)))
                                                         [0 0 0 0] (:namespaces test-report-data))]
                                (if (< 0 (+ (nth rollup-stats 1) (nth rollup-stats 2)))
                                  (change-favicon-to-color "#d00")
                                  (change-favicon-to-color "#0d0"))
                                (dom/div #js {:className "test-count"}
                                         (dom/h2 nil
                                                 (str "Tested " (count (:namespaces test-report-data)) " namespaces containing "
                                                      (nth rollup-stats 3) " assertions. "
                                                      (nth rollup-stats 0) " passed " (nth rollup-stats 1) " failed " (nth rollup-stats 2) " errors"))))))))

(defrecord TestSuite [app-state dom-target reconciler renderer test-item-path]
  ITest
  (render-tests [this] (om/add-root! reconciler renderer (gdom/getElement dom-target)))
  (set-test-result [this status] (let [translated-item-path (translate-item-path app-state @test-item-path)]
                                   (loop [current-test-result-path translated-item-path]
                                     (if (> (count current-test-result-path) 1)
                                       (let [target (get-in @app-state current-test-result-path)
                                             current-status (:status target)]
                                         (if (not (or (= current-status :manual) (= current-status :error) (= current-status :failed)))
                                           (swap! app-state #(assoc-in % (concat current-test-result-path [:status]) status)))
                                         (recur (drop-last 2 current-test-result-path)))))))

  (push-test-item-path [this test-item index] (swap! test-item-path #(conj % :test-items :id (:id test-item) index)))

  (pop-test-item-path [this] (swap! test-item-path #(-> % (pop) (pop) (pop) (pop))))

  (begin-namespace [this name]
    (let [namespaces (get-in @app-state [:top :namespaces])
          namespace-index (first (keep-indexed (fn [idx val] (when (= (:name val) name) idx)) namespaces))
          name-space-location (if namespace-index namespace-index (count namespaces)) ]
      (reset! test-item-path [:namespaces :name name name-space-location])
      (swap! app-state #(assoc-in % [:top :namespaces name-space-location] (make-tests-by-namespace name)))))

  (begin-specification [this spec]
    (let [test-item (make-testitem spec)
          test-items-count (count (get-in @app-state (concat (translate-item-path app-state @test-item-path) [:test-items])))]
      (swap! app-state #(assoc-in % (concat (translate-item-path app-state @test-item-path) [:test-items test-items-count]) test-item))
      (push-test-item-path this test-item test-items-count)))

  (end-specification [this] (pop-test-item-path this))

  (begin-behavior [this behavior]
    (let [test-item (make-testitem behavior)
          parent-test-item (get-in @app-state (translate-item-path app-state @test-item-path))
          test-items-count (count (:test-items parent-test-item))]
      (swap! app-state #(assoc-in % (concat (translate-item-path app-state @test-item-path) [:test-items test-items-count]) test-item))
      (push-test-item-path this test-item test-items-count)))

  (end-behavior [this] (pop-test-item-path this))

  (begin-manual [this behavior]
    (let [test-item (make-manual behavior)
          parent-test-item (get-in @app-state (translate-item-path app-state @test-item-path))
          test-items-count (count (:test-items parent-test-item))]
      (swap! app-state #(assoc-in % (concat (translate-item-path app-state @test-item-path) [:test-items test-items-count]) test-item))
      (push-test-item-path this test-item test-items-count)))

  (end-manual [this]
    (set-test-result this :manual)
    (pop-test-item-path this))


  (begin-provided [this provided]
    (let [test-item (make-testitem provided)
          test-items-count (count (get-in @app-state (concat (translate-item-path app-state @test-item-path) [:test-items])))]
      (swap! app-state #(assoc-in % (concat (translate-item-path app-state @test-item-path) [:test-items test-items-count]) test-item))
      (push-test-item-path this test-item test-items-count)))

  (end-provided [this] (pop-test-item-path this))

  (pass [this] (set-test-result this :passed))

  (error [this detail]
    (let [translated-item-path (translate-item-path app-state @test-item-path)
          current-test-item (get-in @app-state translated-item-path)
          test-result (make-test-result :error detail)
          test-result-path (concat translated-item-path
                                   [:test-results (count (:test-results current-test-item))])]
      (set-test-result this :error)
      (swap! app-state #(assoc-in % test-result-path test-result))))

  (fail [this detail]
    (let [translated-item-path (translate-item-path app-state @test-item-path)
          current-test-item (get-in @app-state translated-item-path)
          test-result (make-test-result :failed detail)
          test-result-path (concat translated-item-path
                                   [:test-results (count (:test-results current-test-item))])]
      (set-test-result this :failed)
      (swap! app-state #(assoc-in % test-result-path test-result))))

  (summary [this stats]
    (let [translated-item-path (translate-item-path app-state @test-item-path)]
      (swap! app-state #(assoc-in % (concat translated-item-path [:passed]) (:passed stats)))
      (swap! app-state #(assoc-in % (concat translated-item-path [:failed]) (:failed stats)))
      (swap! app-state #(assoc-in % (concat translated-item-path [:error]) (:error stats))))))

(defn om-read [{:keys [state]} key _] {:value (get @state key)})
(defmulti om-write om/dispatch)
(defmethod om-write 'filter-all [{:keys [state]} _ _] (swap! state assoc :report/filter :all))
(defmethod om-write 'filter-failed [{:keys [state]} _ _] (swap! state assoc :report/filter :failed))
(defmethod om-write 'filter-manual [{:keys [state]} _ _] (swap! state assoc :report/filter :manual))

(def test-parser (om/parser {:read om-read :mutate om-write}))

(defn new-test-suite
  "Create a new Untangled application with:

  - `:target DOM_ID`: Specifies the target DOM element. The default is 'test'\n


    - `target` :
  - `initial-state` : The state that goes with the top-level renderer

  Additional optional parameters by name:

  - `:history n` : Set the history size. The default is 100.
  "
  [target]
  (let [state (atom {:top (make-testreport)
                     :report/filter :all
                     :time (js/Date.)})]
    (map->TestSuite {:app-state      state
                     :reconciler     (om/reconciler {:state state :parser test-parser})
                     :renderer       TestReport
                     :dom-target     target
                     :test-item-path (atom []) })))
