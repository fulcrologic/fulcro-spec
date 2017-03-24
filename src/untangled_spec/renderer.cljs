(ns untangled-spec.renderer
  (:require
    [clojure.string :as str]
    [cognitect.transit :as transit]
    [goog.string :as gstr]
    [com.stuartsierra.component :as cp]
    [goog.dom :as gdom]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [pushy.core :as pushy]
    [untangled.client.core :as uc]
    [untangled.client.data-fetch :as df]
    [untangled.client.impl.network :as un]
    [untangled.client.mutations :as m]
    [untangled-spec.dom.edn-renderer :refer [html-edn]]
    [untangled-spec.diff :as diff]
    [untangled-spec.selectors :as sel]
    [untangled.icons :as ui.i]
    [untangled.ui.layout :as ui.l]
    [untangled.ui.elements :as ui.e]
    [untangled.websockets.networking :as wn])
  (:import
    (goog.date DateTime)
    (goog.i18n DateTimeFormat)))

(enable-console-print!)

(defn test-item-class [{:keys [fail error pass manual]}]
  (str "test-"
    (cond
      (pos? fail) "fail"
      (pos? error) "error"
      (pos? pass) "pass"
      (pos? manual) "manual"
      :else "pending")))

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

(defn has-status? [p]
  (fn has-status?* [x]
    (or (p (:status x))
        (and
          (seq (:test-items x))
          (seq (filter has-status?* (:test-items x)))))))

(def filters
  (let [report-as (fn [status] #(update % :status select-keys [status]))
        no-test-results #(dissoc % :test-results)]
    {:all (map identity)
     :failing (filter (comp #(some pos? %) (juxt :fail :error) :status))
     :manual  (comp (filter (has-status? #(-> % :manual pos?)))
                (map no-test-results)
                (map (report-as :manual)))
     :passing (comp (filter (comp pos? :pass :status))
                (map (report-as :pass)))
     :pending (comp (filter (has-status? #(->> % vals (apply +) zero?)))
                (map no-test-results)
                (map (report-as :pending)))}))

(defui ^:once Foldable
  Object
  (initLocalState [this] {:folded? true})
  (render [this]
    (let [{:keys [folded?]} (om/get-state this)
          {:keys [render]} (om/props this)
          {:keys [title value classes]} (render folded?)]
      (dom/div #js {:className "foldable"}
        (dom/a #js {:className classes
                    :onClick #(om/update-state! this update :folded? not)}
          (if folded? \u25BA \u25BC)
          (if folded?
            (str (apply str (take 40 title))
              (when (< 40 (count title)) "..."))
            (str title)))
        (dom/div #js {:className (when folded? "hidden")}
          value)))))
(def ui-foldable (om/factory Foldable {:keyfn #(gensym "foldable")}))

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
(def ui-result-line (om/factory ResultLine {:keyfn #(gensym "result-line")}))

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
(def ui-human-diff-lines (om/factory HumanDiffLines {:keyfn #(gensym "human-diff-lines")}))

(defui ^:once HumanDiff
  Object
  (render [this]
    (let [{:keys [diff actual]} (om/props this)
          [fst rst] (split-at 2 diff)]
      (->> (dom/div nil
             (mapv ui-human-diff-lines fst)
             (when (seq rst)
               (ui-foldable {:render
                             (fn [folded?]
                               {:title "& more"
                                :value (mapv ui-human-diff-lines rst)
                                :classes ""})})))
        (dom/td nil)
        (dom/tr nil
          (dom/td nil "DIFFS:"))))))
(def ui-human-diff (om/factory HumanDiff {:keyfn #(gensym "human-diff")}))

(defui ^:once TestResult
  Object
  (render [this]
    (let [{:keys [where message extra actual expected stack diff]} (om/props this)]
      (->> (dom/tbody nil
             (dom/tr nil
               (dom/td #js {:className "test-result-title"}
                 "Where: ")
               (dom/td #js {:className "test-result"}
                 (str/replace (str where)
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
                              :value expected})
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
      (dom/li #js {:className "test-item"}
        (dom/div nil
          (dom/span #js {:className (test-item-class (:status test-item-data))}
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
  (render
    [this]
    (let [{:keys [current-filter] :as tests-by-namespace} (om/props this)]
      (dom/li #js {:className "test-item"}
        (dom/div #js {:className "test-namespace"}
          (dom/h2 #js {:className (test-item-class (:status tests-by-namespace))}
            (str (:name tests-by-namespace)))
          (dom/ul #js {:className "test-list"}
            (sequence (comp (filters current-filter)
                        (map #(assoc % :current-filter current-filter))
                        (map ui-test-item))
              (:test-items tests-by-namespace))))))))
(def ui-test-namespace (om/factory TestNamespace {:keyfn :name}))

(defn filter-button
  ([icon data]
   (filter-button icon data (gensym) (gensym) (constantly nil)))
  ([icon data this-filter current-filter toggle-filter-cb]
   (let [is-active? (= this-filter current-filter)]
     (dom/button #js {:className "c-button c-button--icon"
                      :onClick (toggle-filter-cb this-filter)}
       (ui.i/icon icon :states (cond-> [] is-active? (conj :active)))
       (dom/span #js {:className (cond-> "c-message" is-active?
                                   (str " c-message--primary"))}
         data)))))

(defn find-tests [test-filter namespaces]
  (remove
    (some-fn nil? (comp seq :test-items))
    (apply tree-seq
      #_branch?  (comp seq :test-items)
      #_children (comp (partial sequence (filters test-filter)) :test-items)
      (sequence (filters test-filter) namespaces))))

(defn test-info [{:keys [pass fail error namespaces end-time run-time]}
                 current-filter toggle-filter-cb]
  (let [total (+ pass fail error)
        end-time (.format (new DateTimeFormat "HH:mm:ss")
                   (or (and end-time (.setTime (new DateTime) end-time))
                       (new DateTime)))
        run-time (str/replace-first
                   (gstr/format "%.3fs"
                     (float (/ run-time 1000)))
                   #"^0" "")]
    (if (pos? (+ fail error))
      (change-favicon-to-color "#d00")
      (change-favicon-to-color "#0d0"))
    (dom/span nil
      (filter-button :assignment (count namespaces))
      (filter-button :help total)
      (filter-button :check pass
        :passing current-filter toggle-filter-cb)
      (filter-button :update (count (find-tests :pending namespaces))
        :pending current-filter toggle-filter-cb)
      (filter-button :pan_tool (count (find-tests :manual namespaces))
        :manual current-filter toggle-filter-cb)
      (filter-button :close fail
        :failing current-filter toggle-filter-cb)
      (filter-button :warning error
        :failing current-filter toggle-filter-cb)
      (filter-button :access_time end-time)
      (filter-button :hourglass_empty run-time))))

(defui ^:once SelectorControl
  static om/IQuery
  (query [this] [:selector/id :selector/active?])
  Object
  (render [this]
    (let [{:keys [selector/id selector/active?]} (om/props this)]
      (dom/div #js {:className "c-drawer__action" :key (str id)}
        (ui.e/ui-checkbox
          {:id (str id)
           :checked active?
           :onChange (fn [e]
                       (om/transact! this
                         `[(sel/set-selector
                             ~{:selector/id id
                               :selector/active? (.. e -target -checked)})]))})
        (dom/span #js {} (str id))))))
(def ui-selector-control (om/factory SelectorControl {:keyfn :selector/id}))

(defn test-selectors [selectors]
  (dom/div nil
    (dom/h1 nil "Test Selectors:")
    (map ui-selector-control
      (sort-by :selector/id selectors))))

(defn toolbar-button [toggle-drawer]
  (dom/div #js {:className "c-toolbar__button"}
    (dom/button #js {:className "c-button c-button--icon"
                     :onClick toggle-drawer}
      (ui.i/icon :menu))))

(defn test-header [test-report current-filter toggle-drawer toggle-filter-cb]
  (dom/header #js {:className "u-layout__header c-toolbar c-toolbar--raised"}
    (dom/div #js {:className "c-toolbar__row"}
      (dom/h1 nil "Untangled Spec")
      (dom/div #js {:className "c-toolbar__spacer"})
      (test-info test-report current-filter toggle-filter-cb))
    (toolbar-button toggle-drawer)))

(defn test-main [{:keys [namespaces]} current-filter]
  (dom/main #js {:className "u-layout__content"}
    (dom/article #js {:className "o-article"}
      (ui.l/row {}
        (ui.l/col {:width 12}
          (dom/div #js {:className "test-report"}
            (dom/ul nil
              (sequence
                (comp
                  (filters current-filter)
                  (map #(assoc % :current-filter current-filter))
                  (map ui-test-namespace))
                (sort-by :name namespaces)))))))))

(defui ^:once TestReport
  static uc/InitialAppState
  (initial-state [this _] {:ui/react-key (gensym "UI_REACT_KEY")
                           :ui/current-filter :all})
  static om/IQuery
  (query [this] [:ui/react-key :test-report :ui/current-filter {:selectors (om/get-query SelectorControl)}])
  Object
  (render [this]
    (let [{:keys [ui/react-key test-report selectors ui/current-filter] :as props} (om/props this)
          {:keys [open-drawer?]} (om/get-state this)
          toggle-drawer #(om/update-state! this update :open-drawer? not)
          toggle-filter-cb (fn [f] #(om/transact! this `[(toggle-filter ~{:filter f})]))]
      (dom/div #js {:key react-key :className "u-layout"}
        (dom/div #js {:className "u-layout__page u-layout__page--fixed"}
          (test-header test-report current-filter toggle-drawer toggle-filter-cb)
          (dom/div #js {:className (cond-> "c-drawer" open-drawer? (str " is-open"))}
            (dom/div #js {:className "c-drawer__header"}
              (dom/img #js {:src "img/logo.png" :height 35 :width 35
                            :onClick toggle-drawer})
              (dom/h1 nil "Untangled Spec"))
            (test-selectors selectors))
          (dom/div #js {:className (cond-> "c-backdrop" open-drawer? (str " is-active"))
                        :onClick toggle-drawer})
          (test-main test-report current-filter))))))

(defmethod m/mutate `render-tests [{:keys [state]} _ new-report]
  {:action #(swap! state assoc :test-report new-report)})
(defmethod wn/push-received `render-tests
  [{:keys [reconciler]} {test-report :msg}]
  (om/transact! (om/app-root reconciler)
    `[(render-tests ~test-report)]))

(defrecord TestRenderer [root target with-websockets? runner-atom]
  cp/Lifecycle
  (start [this]
    (let [app (uc/new-untangled-client
                :networking (if with-websockets?
                              (wn/make-channel-client "/_untangled_spec_chsk")
                              (reify un/UntangledNetwork
                                (start [this app] this)
                                (send [this edn ok err]
                                  (ok ((om/parser @runner-atom) @runner-atom edn)))))
                :started-callback
                (fn [app]
                  (df/load app :selectors SelectorControl
                    {:post-mutation `sel/set-selectors})))]
      (assoc this :app (uc/mount app root target))))
  (stop [this]
    (assoc this :app nil)))

(defn make-test-renderer [{:keys [with-websockets?] :or {with-websockets? true}}]
  (map->TestRenderer
    {:with-websockets? with-websockets?
     :runner-atom (atom nil)
     :root TestReport
     :target "untangled-spec-report"}))
