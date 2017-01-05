(ns untangled-spec.reporters.browser
  (:require
    [om.dom :as dom]
    [goog.dom :as gdom]
    [om.next :as om :refer-macros [defui]]
    [cljs-uuid-utils.core :as uuid]
    [clojure.string :as s]

    [untangled-spec.dom.edn-renderer :refer [html-edn]]
    [untangled-spec.reporters.impl.browser :as impl]
    [untangled-spec.reporters.impl.diff :as diff]
    [pushy.core :as pushy]))

(defn unique-key-fn [ns-str]
  (let [c (atom 0)]
    #(str ns-str "_" (swap! c inc))))

(defn has-status? [p]
  (fn has-status?* [x]
    (or (p (:status x))
        (and
          (seq (:test-items x))
          (seq (filter has-status?* (:test-items x)))))))

(def filters
  {:all (map identity)
   :failing (filter (comp #(some pos? %) (juxt :failed :error) :status))
   :manual  (comp (filter (has-status? #(-> % :manual pos?)))
              (map #(dissoc % :test-results))
              (map #(update % :status select-keys [:manual])))
   :passing (filter (comp pos? :passed :status))
   :pending (comp (filter (has-status? #(->> % vals (apply +) zero?)))
              (map #(dissoc % :test-results))
              (map #(update % :status select-keys [:pending])))})

(defui ^:once Foldable
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
(def ui-foldable (om/factory Foldable {:keyfn (unique-key-fn "foldable")}))

(defui ^:once ResultLine
  Object
  (render [this]
    (let [{:keys [title value stack type]} (om/props this)]
      (dom/tr nil
        (dom/td #js {:className (str "test-result-title "
                                  (name type))}
          title)
        (dom/td #js {:className "test-result"}
          (dom/code nil
            (ui-foldable
              {:render (fn [folded?]
                         {:title (if stack (str value)
                                   (if folded? (str value) title))
                          :value (if stack stack (if-not folded? (html-edn value)))
                          :classes (if stack "stack")})})))))))
(def ui-result-line (om/factory ResultLine {:keyfn (unique-key-fn "result-line")}))

(defui ^:once HumanDiffLines
  Object
  (render [this]
    (let [d (om/props this)
          {:keys [exp got path]} (diff/extract d)]
      (dom/table #js {:className "human-diff-lines"}
        (dom/tbody nil
          (when (seq path)
            (dom/tr #js {:className "path"}
              (dom/td nil "at: ")
              (dom/td nil (str path))))
          (dom/tr #js {:className "expected"}
            (dom/td nil "exp: ")
            (dom/td nil (html-edn exp)))
          (dom/tr #js {:className "actual"}
            (dom/td nil "got: ")
            (dom/td nil (html-edn got))))))))
(def ui-human-diff-lines (om/factory HumanDiffLines {:keyfn (unique-key-fn "human-diff-lines")}))

(defui ^:once HumanDiff
  Object
  (render [this]
    (let [{:keys [diff actual]} (om/props this)
          [fst rst] (split-at 2 diff)]
      (->> (dom/div nil
             (when (associative? actual)
               (ui-foldable {:render (fn [folded?]
                                       {:title "DIFF"
                                        :value (html-edn actual diff)})}))
             (mapv ui-human-diff-lines fst)
             (if (seq rst)
               (ui-foldable {:render
                             (fn [folded?]
                               {:title "& more"
                                :value (mapv ui-human-diff-lines rst)
                                :classes ""})})))
        (dom/td nil)
        (dom/tr #js {:className "human-diff"}
          (dom/td nil "DIFFS:"))))))
(def ui-human-diff (om/factory HumanDiff {:keyfn (unique-key-fn "human-diff")}))

(defui ^:once TestResult
  Object
  (render [this]
    (let [{:keys [where message extra actual expected stack diff]} (om/props this)]
      (->> (dom/tbody nil
             (dom/tr nil
               (dom/td #js {:className "test-result-title"}
                 "Where: ")
               (dom/td #js {:className "test-result"}
                 (s/replace (str where)
                   #"G__\d+" "")))
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

(defui ^:once TestItem
  Object
  (render [this]
    (let [{:keys [current-filter] :as test-item-data} (om/props this)]
      (dom/li #js {:className "test-item "}
        (dom/div nil
          (dom/span #js {:className (impl/itemclass (:status test-item-data))}
            (:name test-item-data))
          (dom/ul #js {:className "test-list"}
            (mapv ui-test-result
              (:test-results test-item-data)))
          (dom/ul #js {:className "test-list"}
            (sequence
              (comp (filters current-filter)
                (map #(assoc % :current-filter current-filter))
                (map ui-test-item))
              (:test-items test-item-data))))))))
(def ui-test-item (om/factory TestItem {:keyfn :id}))

(defui ^:once TestNamespace
  Object
  (initLocalState [this] {:folded? false})
  (render
    [this]
    (let [{:keys [current-filter] :as tests-by-namespace} (om/props this)
          {:keys [folded?]} (om/get-state this)]
      (dom/li #js {:className "test-item"}
        (dom/div #js {:className "test-namespace"}
          (dom/a #js {:href "javascript:void(0)"
                      :style #js {:textDecoration "none"} ;; TODO: refactor to css
                      :onClick #(om/update-state! this update :folded? not)}
            (dom/h2 #js {:className (impl/itemclass (:status tests-by-namespace))}
              (if folded? \u25BA \u25BC)
              " Testing " (:name tests-by-namespace)))
          (dom/ul #js {:className (if folded? "hidden" "test-list")}
            (sequence (comp (filters current-filter)
                        (map #(assoc % :current-filter current-filter))
                        (map ui-test-item))
              (:test-items tests-by-namespace))))))))
(def ui-test-namespace (om/factory TestNamespace {:keyfn :name}))

(defui ^:once FilterSelector
  Object
  (render [this]
    (let [{:keys [this-filter current-filter]} (om/props this)]
      (dom/a #js {:href (str "#" (name this-filter))
                  :className (if (= this-filter current-filter)
                               "selected" "")}
        (name this-filter)))))
(def ui-filter-selector (om/factory FilterSelector {:keyfn identity}))

(defui ^:once Filters
  Object
  (render [this]
    (let [{:keys [current-filter]} (om/props this)]
      (dom/div #js {:name "filters" :className "filter-controls"}
        (dom/label #js {:htmlFor "filters"} "Filter: ")
        (sequence
          (comp (map #(hash-map
                        :this-filter %
                        :current-filter current-filter))
            (map ui-filter-selector))
          (keys filters))))))
(def ui-filters (om/factory Filters {}))

(defui ^:once TestCount
  Object
  (render [this]
    (let [{:keys [passed failed error namespaces]} (om/props this)
          total (+ passed failed error)]
      (if (pos? (+ failed error))
        (impl/change-favicon-to-color "#d00")
        (impl/change-favicon-to-color "#0d0"))
      (dom/div #js {:className "test-count"}
        (dom/h2 nil
          (str "Tested " (count namespaces) " namespaces containing "
            total  " assertions. "
            passed " passed "
            failed " failed "
            error  " errors"))))))
(def ui-test-count (om/factory TestCount {:keyfn identity}))

(defui ^:once TestReport
  static om/IQuery
  (query [this] [:top :report/filter])
  Object
  (render [this]
    (let [{test-report-data :top current-filter :report/filter} (om/props this)]
      (dom/section #js {:className "test-report"}
        (ui-filters {:current-filter current-filter})
        (dom/ul #js {:className "test-list"}
          (sequence
            (comp
              (filters current-filter)
              (map #(assoc % :current-filter current-filter))
              (map ui-test-namespace))
            (:namespaces test-report-data)))
        (ui-test-count test-report-data)))))
