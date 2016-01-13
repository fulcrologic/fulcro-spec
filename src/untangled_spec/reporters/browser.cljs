(ns untangled-spec.reporters.browser
  (:import [goog Delay])
  (:require
    goog.object
    [om.dom :as dom]
    [goog.dom :as gdom]
    [om.next :as om :refer-macros [defui]]
    [cljs-uuid-utils.core :as uuid]

    [untangled-spec.dom.edn-renderer :refer [html-edn]]
    [untangled-spec.reporters.impl.browser :as impl]))

(defui Foldable
       Object
       (initLocalState [this] {:folded? true})
       (render [this]
               (let [{:keys [folded?]} (om/get-state this)
                     {:keys [render]} (om/props this)
                     {:keys [title value classes]} (render folded?)]
                 (dom/div nil
                          (dom/a #js {:href "javascript:void(0);"
                                      :className classes
                                      :onClick #(om/update-state! this update :folded? not)}
                                 (if folded? \u25BA \u25BC)
                                 (if folded?
                                   (str (apply str (take 40 title))
                                        (when (< 40 (count title)) "..."))
                                   (str title)))
                          (dom/div #js {:className (when folded? "hidden")}
                                   value)))))
(def ui-foldable (om/factory Foldable))

(defui ResultLine
       Object
       (render [this]
               (let [{:keys [title value stack] type- :type} (om/props this)]
                 (dom/tr nil
                         (dom/td #js {:className (str "test-result-title "
                                                      (name type-))}
                                 title)
                         (dom/td #js {:className "test-result"}
                                 (dom/code nil
                                           (ui-foldable
                                             {:render (fn [folded?]
                                                        {:title (if stack (str value)
                                                                  (if folded? (str value) title))
                                                         :value (if stack stack (if-not folded? (html-edn value)))
                                                         :classes (if stack "stack")})})))))))
(def ui-result-line (om/factory ResultLine))

(defui HumanDiffLines
       Object
       (render [this]
               (let [d (om/props this)
                     path (vec (drop-last d))
                     [_ got _ exp] (last d)]
                 (dom/table #js {:className "human-diff-lines"}
                            (dom/tbody nil
                                       (when (seq path)
                                         (dom/tr #js {:className "path"}
                                                 (dom/td nil "at: ")
                                                 (dom/td nil (str path))))
                                       (dom/tr #js {:className "actual"}
                                               (dom/td nil "got: ")
                                               (dom/td nil (html-edn (impl/?ø got))))
                                       (dom/tr #js {:className "expected"}
                                               (dom/td nil "exp: ")
                                               (dom/td nil (html-edn (impl/?ø exp)))))))))
(def ui-human-diff-lines (om/factory HumanDiffLines {:keyfn (let [c (atom 0)]
                                                              #(swap! c inc))}))

(defui HumanDiff
       Object
       (render [this]
               (let [{:keys [diff actual]} (om/props this)
                     [fst rst] (split-at 2 diff)]
                 (->> (dom/div nil
                               (mapv ui-human-diff-lines fst)
                               (ui-foldable {:render
                                             (fn [folded?]
                                               {:title "& more"
                                                :value (mapv ui-human-diff-lines rst)
                                                :classes ""})}))
                      (dom/td nil)
                      (dom/tr #js {:className "human-diff"}
                              (dom/td nil "DIFFS:"))))))
(def ui-human-diff (om/factory HumanDiff))

(defui TestResult
       Object
       (render [this]
               (let [{:keys [where message extra actual expected stack diff]} (om/props this)]
                 (->> (dom/tbody nil
                                 (dom/tr nil
                                         (dom/td #js {:className "test-result-title"}
                                                 "Where: ")
                                         (dom/td #js {:className "test-result"}
                                                 (str where)))
                                 (when message
                                   (ui-result-line {:type :normal
                                                    :title "ASSERTION: "
                                                    :value message}))
                                 (ui-result-line {:type :normal
                                                  :title "Actual: "
                                                  :value actual
                                                  :stack stack})
                                 (ui-result-line {:type :normal
                                                  :title "Expected: "
                                                  :value (or expected "")})
                                 (when extra
                                   (ui-result-line {:type :normal
                                                    :title "Message: "
                                                    :value extra}))
                                 (when diff
                                   (ui-human-diff {:actual actual
                                                   :diff diff})))
                      (dom/table nil)
                      (dom/li nil)))))
(def ui-test-result (om/factory TestResult {:keyfn :id}))

(declare ui-test-item)

(defui TestItem
       Object
       (render [this]
               (let [test-item-data (om/props this)
                     filter (:report/filter test-item-data) ]
                 (dom/li #js {:className "test-item "}
                         (dom/div #js {:className (impl/filter-class test-item-data)}
                                  (dom/span #js {:className (impl/itemclass (:status test-item-data))}
                                            (:name test-item-data))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv ui-test-result
                                                (:test-results test-item-data)))
                                  (dom/ul #js {:className "test-list"}
                                          (mapv (comp ui-test-item #(assoc % :report/filter filter))
                                                (:test-items test-item-data))))))))
(def ui-test-item (om/factory TestItem {:keyfn :id}))

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
                            (dom/a #js {:href "javascript:void(0)"
                                        :style #js {:textDecoration "none"} ;; TODO: refactor to css
                                        :onClick   #(om/update-state! this update :folded? not)}
                                   (dom/h2 #js {:className (impl/itemclass (:status tests-by-namespace))}
                                           (if folded? \u25BA \u25BC)
                                           " Testing " (:name tests-by-namespace)))
                            (dom/ul #js {:className (if folded? "hidden" "test-list")}
                                    (mapv (comp ui-test-item #(assoc % :report/filter filter))
                                          (:test-items tests-by-namespace))))))))
(def ui-test-namespace (om/factory TestNamespace {:keyfn :name}))

(defui FilterSelector
       Object
       (render [this]
               (let [{:keys [current-filter this-filter set-filter!]} (om/props this)]
                 (dom/a #js {:href "#"
                             :className (if (= current-filter this-filter)
                                          "selected" "")
                             :onClick (set-filter! this-filter)}
                        (str this-filter)))))
(def ui-filter-selector (om/factory FilterSelector {:keyfn :this-filter}))

(defui Filters
       Object
       (render [this]
               (let [{:current-filter :report/filter
                      :keys [set-filter!]} (om/props this)]
                 (dom/div #js {:name "filters" :className "filter-controls"}
                          (dom/label #js {:htmlFor "filters"} "Filter: ")
                          (mapv #(ui-filter-selector {:current-filter current-filter
                                                      :set-filter! set-filter!
                                                      :this-filter %})
                                [:all :manual :failed])))))
(def ui-filters (om/factory Filters ))

(defui TestCount
       Object
       (render [this]
               (let [{:keys [passed failed error namespaces]} (om/props this)
                     total (+ passed failed error)]
                 (if (< 0 (+ failed error))
                   (impl/change-favicon-to-color "#d00")
                   (impl/change-favicon-to-color "#0d0"))
                 (dom/div #js {:className "test-count"}
                          (dom/h2 nil
                                  (str "Tested " (count namespaces) " namespaces containing "
                                       total  " assertions. "
                                       passed " passed "
                                       failed " failed "
                                       error  " errors"))))))
(def ui-test-count (om/factory TestCount))

(defui TestReport
       static om/IQuery
       (query [this] [:top :report/filter])
       Object
       (render [this]
               (let [props (om/props this)
                     test-report-data (-> props :top)
                     current-filter (-> props :report/filter)
                     set-filter! (fn [new-filter]
                                   #(om/transact! this `[(~'set-filter {:new-filter ~new-filter})]))]
                 (dom/section #js {:className "test-report"}
                              (ui-filters {:report/filter current-filter
                                           :set-filter! set-filter!})
                              (dom/ul #js {:className "test-list"}
                                      (->> (:namespaces test-report-data)
                                           (remove #(when (= :failed current-filter)
                                                      (not (#{:failed :error} (:status %)))))
                                           (mapv (comp ui-test-namespace
                                                       #(assoc % :report/filter current-filter)))))
                              (ui-test-count test-report-data)))))
