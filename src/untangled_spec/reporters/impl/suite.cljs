(ns ^:figwheel-always untangled-spec.reporters.impl.suite
  (:require
    goog.object
    [goog.dom :as gdom]
    [om.next :as om]
    [untangled-spec.reporters.browser :as browser]
    [untangled-spec.reporters.impl.base-reporter :as impl]

    [bidi.bidi :as bidi]
    [pushy.core :as pushy]))

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
          :otherwise (->> (map-indexed vector items)
                          (find-first #(= value (id-keyword (second %))))
                          (first)))))

(def path-ele-length 4)

(defn resolve-data-path [state path-seq]
  (reduce (fn [real-path path-ele]
            (if (sequential? path-ele)
              (do
                (if (not= path-ele-length (count path-ele))
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
      (let [resolved-path (resolve-data-path data (vector (seq (take path-ele-length path))))
            context-data (get-in data resolved-path)]
        (recur context-data (drop path-ele-length path) (concat result resolved-path))))))

(defrecord TestSuite [app-state dom-target reconciler renderer test-item-path]
  ITest
  (render-tests [this]
    (om/add-root! reconciler renderer (gdom/getElement dom-target)))

  (set-test-result [this status]
    (impl/set-test-result app-state
                          (translate-item-path app-state @test-item-path)
                          status))

  (push-test-item-path [this test-item index]
    (swap! test-item-path conj :test-items :id (:id test-item) index))

  (pop-test-item-path [this]
    (swap! test-item-path (apply comp (repeat path-ele-length pop))))

  (begin-namespace [this name]
    (let [namespaces (get-in @app-state [:top :namespaces])
          name-space-location (impl/get-namespace-location namespaces name) ]
      (reset! test-item-path [:namespaces :name name name-space-location])
      (swap! app-state #(assoc-in % [:top :namespaces name-space-location]
                                  (impl/make-tests-by-namespace name)))))

  (begin-specification [this x]
    (let [path (translate-item-path app-state @test-item-path)
          [test-item test-items-count] (impl/begin x app-state path)]
      (push-test-item-path this test-item test-items-count)))

  (end-specification [this] (pop-test-item-path this))

  (begin-behavior [this x]
    (let [path (translate-item-path app-state @test-item-path)
          [test-item test-items-count] (impl/begin x app-state path)]
      (push-test-item-path this test-item test-items-count)))

  (end-behavior [this] (pop-test-item-path this))

  (begin-manual [this x]
    (let [path (translate-item-path app-state @test-item-path)
          [test-item test-items-count] (impl/begin x app-state path)]
      (push-test-item-path this test-item test-items-count)))

  (end-manual [this]
    (set-test-result this :manual)
    (pop-test-item-path this))

  (begin-provided [this x]
    (let [path (translate-item-path app-state @test-item-path)
          [test-item test-items-count] (impl/begin x app-state path)]
      (push-test-item-path this test-item test-items-count)))

  (end-provided [this] (pop-test-item-path this))

  (pass [this] (set-test-result this :passed))

  (error [this detail]
    (impl/error detail app-state
                (translate-item-path app-state @test-item-path)))

  (fail [this detail]
    (impl/fail detail app-state
               (translate-item-path app-state @test-item-path)))

  (summary [this stats]
    (impl/summary stats [:top] app-state)))

(defn om-read [{:keys [state ast query]} k _]
  {:value (case (:type ast) :prop (get @state k)
            (om/db->tree query (get @state k) @state))})
(defmulti om-write om/dispatch)
(defmethod om-write 'set-filter [{:keys [state]} _ {:keys [new-filter]}]
  (swap! state assoc :report/filter new-filter))

(def test-parser (om/parser {:read om-read :mutate om-write}))

(def app-routes
  ["" (into {}
        (map (juxt name identity)
          (keys browser/filters)))])

(defn set-page! [reconciler]
  (fn [new-filter]
    (om/transact! reconciler
      `[(~'set-filter ~{:new-filter new-filter})])))

(defn new-test-suite [target]
  (let [state (atom {:top (impl/make-testreport)
                     :report/filter :all
                     :time (js/Date.)})
        reconciler (om/reconciler {:state state :parser test-parser})
        history (with-redefs [pushy/update-history!
                              #(doto %
                                 (.setUseFragment true)
                                 (.setPathPrefix "")
                                 (.setEnabled true))]
                  (pushy/pushy (set-page! reconciler)
                               (partial bidi/match-route app-routes)
                               :identity-fn :handler))]
    (pushy/start! history)
    (map->TestSuite {:app-state      state
                     :reconciler     reconciler
                     :renderer       browser/TestReport
                     :dom-target     target
                     :test-item-path (atom [])})))
