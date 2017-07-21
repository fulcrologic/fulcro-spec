(ns fulcro-spec.renderer
  (:require
    [clojure.string :as str]
    [cognitect.transit :as transit]
    [goog.string :as gstr]
    [com.stuartsierra.component :as cp]
    [goog.dom :as gdom]
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [pushy.core :as pushy]
    [fulcro.client.core :as fc]
    [fulcro.client.data-fetch :as df]
    [fulcro.client.network :as fcn]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro-spec.dom.edn-renderer :refer [html-edn]]
    [fulcro-spec.diff :as diff]
    [fulcro-spec.selectors :as sel]
    [fulcro.websockets.networking :as wn])
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
  (let [favicon (.getElementById js/document "favicon")]
    (set! (.-href favicon) (color-favicon-data-url color))))

(defn has-status? [p]
  (fn has-status?* [x]
    (or (p (:status x))
      (and
        (seq (:test-items x))
        (seq (filter has-status?* (:test-items x)))))))

(def filters
  (let [report-as       (fn [status] #(update % :status select-keys [status]))
        no-test-results #(dissoc % :test-results)]
    {:all     (map identity)
     nil      (map identity)
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
                    :onClick   #(om/update-state! this update :folded? not)}
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
                         {:title   (if stack (str value)
                                             (if folded? (str value) title))
                          :value   (if stack stack (if-not folded? (html-edn value)))
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
                               {:title   "& more"
                                :value   (mapv ui-human-diff-lines rst)
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
               (ui-result-line {:type  :normal
                                :title "ASSERTION: "
                                :value message}))
             (ui-result-line {:type  :normal
                              :title "Actual: "
                              :value actual
                              :stack stack})
             (ui-result-line {:type  :normal
                              :title "Expected: "
                              :value expected})
             (when extra
               (ui-result-line {:type  :normal
                                :title "Message: "
                                :value extra}))
             (when diff
               (ui-human-diff {:actual actual
                               :diff   diff})))
        (dom/table nil)
        (dom/li nil)))))
(def ui-test-result (om/factory TestResult {:keyfn :id}))

(declare ui-test-item)

(defui ^:once TestItem
  Object
  (render [this]
    (let [{:keys [id current-filter] :as test-item-data} (om/props this)]
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
      (when (seq (:test-items tests-by-namespace))
        (dom/li #js {:className "test-item"}
          (dom/div #js {:className "test-namespace"}
            (dom/h2 #js {:className (test-item-class (:status tests-by-namespace))}
              (str (:name tests-by-namespace)))
            (dom/ul #js {:className "test-list"}
              (sequence (comp (filters current-filter)
                          (map #(assoc % :current-filter current-filter))
                          (map ui-test-item))
                (sort-by (comp :line :form-meta) (:test-items tests-by-namespace))))))))))
(def ui-test-namespace (om/factory TestNamespace {:keyfn :name}))

(def material-icon-paths
  {:access_time     "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"
   :assignment      "M19 3h-4.18C14.4 1.84 13.3 1 12 1c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z"
   :check           "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"
   :close           "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
   :help            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"
   :hourglass_empty "M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zm-4-5l-4-4V4h8v3.5l-4 4z"
   :menu            "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"
   :pan_tool        "M23 5.5V20c0 2.2-1.8 4-4 4h-7.3c-1.08 0-2.1-.43-2.85-1.19L1 14.83s1.26-1.23 1.3-1.25c.22-.19.49-.29.79-.29.22 0 .42.06.6.16.04.01 4.31 2.46 4.31 2.46V4c0-.83.67-1.5 1.5-1.5S11 3.17 11 4v7h1V1.5c0-.83.67-1.5 1.5-1.5S15 .67 15 1.5V11h1V2.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5V11h1V5.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5z"
   :update          "M21 10.12h-6.78l2.74-2.82c-2.73-2.7-7.15-2.8-9.88-.1-2.73 2.71-2.73 7.08 0 9.79 2.73 2.71 7.15 2.71 9.88 0C18.32 15.65 19 14.08 19 12.1h2c0 1.98-.88 4.55-2.64 6.29-3.51 3.48-9.21 3.48-12.72 0-3.5-3.47-3.53-9.11-.02-12.58 3.51-3.47 9.14-3.47 12.65 0L21 3v7.12zM12.5 8v4.25l3.5 2.08-.72 1.21L11 13V8h1.5z"
   :warning         "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"})

(def state-mods {:is  #{:active :open :optional :collapsed :passive :positive :negative :neutral :live :alterable :informative :featured :disabled :indeterminate :invalid :error}
                 :has #{:descendents :focus :actions}})

(defn concat-class-string
  "`fragments` is a collection of fragmentsto concatinate."
  ([fragments]
   (concat-class-string "" "" fragments))
  ([type fragments]
   (concat-class-string "" type fragments))
  ([base-str type fragments]
   (reduce
     (fn [acc n]
       (str acc " " (str base-str type (name n))))
     base-str
     fragments)))

(defn concat-state-string
  [states]
  (reduce (fn [acc n] (let [middle (when (not-empty acc) " ")]
                        (cond
                          ((:is state-mods) n) (str acc middle (str "is-" (name n)))
                          ((:has state-mods) n) (str acc middle (str "has-" (name n)))
                          :else acc)))
    ""
    states))

(defn title-case
  "Capitalize every word in a string"
  [s]
  (->> (str/split (str s) #"\b")
    (map str/capitalize)
    str/join))

(defn icon [icon-path & {:keys [width height modifiers states className onClick]}]
  (assert (keyword? icon-path) "Must pass a :key")
  (let [add-class  (fn [attrs])
        path-check (icon-path material-icon-paths)
        icon-name  (str/replace (name icon-path) #"_" "-")]
    (when-not (str/blank? path-check)
      (dom/svg (clj->js
                 (cond->
                   {:className       (str/join " " [(concat-class-string "c-icon" "--" modifiers)
                                                    (str "c-icon--" icon-name)
                                                    (concat-state-string states)
                                                    (concat-class-string className)])
                    :version         "1.1"
                    :xmlns           "http://www.w3.org/2000/svg"
                    :width           "24"
                    :height          "24"
                    :aria-labelledby "title"
                    :role            "img"
                    :viewBox         "0 0 24 24"}
                   onClick (assoc :onClick #(onClick))))
        (dom/title nil (str (title-case (str/replace (name icon-path) #"_" " "))))
        (dom/path #js {:d path-check})))))

(defn filter-button
  ([icon-type data]
   (filter-button icon-type data (gensym) (gensym) (constantly nil)))
  ([icon-type data this-filter current-filter toggle-filter-cb]
   (let [is-active? (= this-filter current-filter)]
     (dom/button #js {:className "c-button c-button--icon"
                      :onClick   (toggle-filter-cb this-filter)}
       (icon icon-type :states (cond-> [] is-active? (conj :active)))
       (dom/span #js {:className (cond-> "c-message" is-active?
                                   (str " c-message--primary"))}
         data)))))

(defn find-tests [test-filter namespaces]
  (remove
    (some-fn nil? (comp seq :test-items))
    (apply tree-seq
      #_branch? (comp seq :test-items)
      #_children (comp (partial sequence (filters test-filter)) :test-items)
      (sequence (filters test-filter) namespaces))))

(defn test-info [{:keys [pass fail error namespaces end-time run-time]}
                 current-filter toggle-filter-cb]
  (let [total    (+ pass fail error)
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

(let [render-input (fn [{:keys [type id] :as props}]
                     (dom/span nil
                       (dom/input (clj->js props))
                       (dom/label #js {:htmlFor id} \u00A0)))]
  (defn ui-checkbox
    "Render a checkbox (not the label). Props is a normal clj(s) map with React/HTML attributes plus:

     `:className` - additional class stylings to apply to the top level of the checkbox
     `:id` string - Unique DOM ID. Required for correct rendering.
     `:checked` - true, false, or :partial"
    [{:keys [id state checked className] :as props}]
    (assert id "DOM ID is required on checkbox")
    (let [classes (str className " c-checkbox" (when (= :partial checked) " is-indeterminate"))
          checked (boolean checked)
          attrs   (assoc props :type "checkbox" :checked checked :className classes)]
      (render-input attrs))))

(defui ^:once SelectorControl
  static om/IQuery
  (query [this] [:selector/id :selector/active?])
  Object
  (render [this]
    (let [{:keys [selector/id selector/active?]} (om/props this)]
      (dom/div #js {:className "c-drawer__action" :key (str id)}
        (ui-checkbox
          {:id       (str id)
           :checked  active?
           :onChange (fn [e]
                       (om/transact! this
                         `[(sel/set-selector
                             ~{:selector/id      id
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
                     :onClick   toggle-drawer}
      (icon :menu))))

(defn test-header [test-report current-filter toggle-drawer toggle-filter-cb]
  (dom/header #js {:className "u-layout__header c-toolbar c-toolbar--raised"}
    (dom/div #js {:className "c-toolbar__row"}
      (dom/h1 nil "Fulcro Spec")
      (dom/div #js {:className "c-toolbar__spacer"})
      (test-info test-report current-filter toggle-filter-cb))
    (toolbar-button toggle-drawer)))

(defn row
  "Generate a layout row. This is a div container for a row in a 12-wide grid responsive layout.
  Rows should contain layout columns generated with the `col` function of this namespace.

  The properties are normal DOM attributes as a cljs map and can include standard React DOM properties.

  `:distribute-extra-columns` can be :between or :around, and indicates where to put unused columns.
      - :between Unused column space is even distributed between columns
          2COL _____ 2COL _____ 2COL
      - :around Unused column space is even distributed around columns
          __ 2COL __ 2COL __ 2COL __

  `:halign` can be :start, :center, or :end for positioning a single child column in that position
  `:valign` can be :top, :middle, or :bottom and will affect the vertical positioning of nested cells that do not
  share a common height."
  [{:keys [distribute-extra-columns halign valign density] :as props} & children]
  {:pre [(contains? #{nil :between :around} distribute-extra-columns)
         (contains? #{nil :start :center :end} halign)
         (contains? #{nil :top :middle :bottom} valign)
         (contains? #{nil :collapse :wide :break} density)]}
  (let [className (or (:className props) "")
        classes   (cond-> className
                    :always (str " u-row")
                    distribute-extra-columns (str " u-" (name distribute-extra-columns))
                    halign (str " u-" (name halign))
                    valign (str " u-" (name valign))
                    density (str " u-row--" (name density)))
        attrs     (-> props
                    (dissoc :distribute-extra-columns :halign :valign :density)
                    (assoc :className classes)
                    clj->js)]
    (apply dom/div attrs children)))

(defn col
  "Output a div that represents a column in the 12-column responsive grid.

  NOTE: halign works on anything, valign on on rows"
  [{:keys [width sm-width md-width lg-width xl-width halign valign
           push sm-push md-push lg-push xl-push] :as props} & children]
  {:pre [(contains? #{nil :start :center :end} halign)
         (contains? #{nil :top :middle :bottom} valign)]}
  (let [classes (cond-> (:className props)
                  width (str " u-column--" width)
                  halign (str " u-" (name halign))
                  valign (str " u-" (name valign))
                  sm-width (str " u-column--" sm-width "@sm")
                  md-width (str " u-column--" md-width "@md")
                  lg-width (str " u-column--" lg-width "@lg")
                  xl-width (str " u-column--" xl-width "@xl")
                  push (str " u-push--" push)
                  sm-push (str " u-push--" sm-push "@sm")
                  md-push (str " u-push--" md-push "@md")
                  lg-push (str " u-push--" lg-push "@lg")
                  xl-push (str " u-push--" xl-push "@xl"))
        attrs   (-> props
                  (dissoc :width :halign :valign :sm-width :md-width :lg-width :xl-width :push :sm-push :md-push :lg-push :xl-push)
                  (assoc :className classes)
                  clj->js)]
    (apply dom/div attrs children)))

(defn test-main [{:keys [namespaces]} current-filter]
  (dom/main #js {:className "u-layout__content"}
    (dom/article #js {:className "o-article"}
      (row {}
        (col {:width 12}
          (dom/div #js {:className "test-report"}
            (dom/ul nil
              (sequence
                (comp
                  (filters current-filter)
                  (map #(assoc % :current-filter current-filter))
                  (map ui-test-namespace))
                (sort-by :name namespaces)))))))))

(defui ^:once TestReport
  static fc/InitialAppState
  (initial-state [this _] {:ui/react-key      (gensym "UI_REACT_KEY")
                           :compile-error     nil
                           :ui/current-filter :all})
  static om/IQuery
  (query [this] [:ui/react-key :test-report :compile-error :ui/current-filter {:selectors (om/get-query SelectorControl)}])
  Object
  (render [this]
    (let [{:keys [ui/react-key compile-error test-report selectors ui/current-filter] :as props} (om/props this)
          {:keys [open-drawer?]} (om/get-state this)
          toggle-drawer    #(om/update-state! this update :open-drawer? not)
          toggle-filter-cb (fn [f] #(om/transact! this `[(toggle-filter ~{:filter f})]))]
      (dom/div #js {:key react-key :className "u-layout"}
        (dom/div #js {:className "u-layout__page u-layout__page--fixed"}
          (test-header test-report current-filter toggle-drawer toggle-filter-cb)
          (dom/div #js {:className (cond-> "c-drawer" open-drawer? (str " is-open"))}
            (dom/div #js {:className "c-drawer__header"}
              (dom/img #js {:src     "img/logo.png" :height 35 :width 35
                            :onClick toggle-drawer})
              (dom/h1 nil "Fulcro Spec"))
            (test-selectors selectors))
          (dom/div #js {:className (cond-> "c-backdrop" open-drawer? (str " is-active"))
                        :onClick   toggle-drawer})
          (if compile-error
            (dom/h1 nil compile-error)
            (test-main test-report current-filter)))))))

(defmutation render-tests
  "Om muation: render the tests"
  [new-report]
  (action [{:keys [state]}]
    (swap! state assoc :test-report new-report)))

(defmutation show-compile-error
  "Om mutation: show a compiler error"
  [{:keys [error]}]
  (action [{:keys [state]}]
    (swap! state assoc :compile-error error)))

(defmutation clear-compile-error
  "Om mutation: Clear the compiler error"
  [ignored]
  (action [{:keys [state]}]
    (swap! state assoc :compile-error false)))

(defmethod wn/push-received `render-tests
  [{:keys [reconciler]} {test-report :msg}]
  (om/transact! (om/app-root reconciler)
    `[(clear-compile-error {}) (render-tests ~test-report)]))

(defmethod wn/push-received `show-compile-error
  [{:keys [reconciler]} {message :msg}]
  (om/transact! (om/app-root reconciler) `[(show-compile-error ~{:error message})]))

(defrecord TestRenderer [root target with-websockets? runner-atom]
  cp/Lifecycle
  (start [this]
    (try
      (let [app (fc/new-fulcro-client
                 :networking (if with-websockets?
                               (wn/make-channel-client "/_fulcro_spec_chsk")
                               (reify fcn/FulcroNetwork
                                 (start [this app] this)
                                 (send [this edn ok err]
                                   (ok ((om/parser @runner-atom) @runner-atom edn)))))
                 :started-callback
                 (fn [app]
                   (df/load app :selectors SelectorControl
                     {:post-mutation `sel/set-selectors})))]
       (assoc this :app (fc/mount app root target)))
      (catch js/Object e (js/console.log "Startup Failed: " e))))
  (stop [this]
    (assoc this :app nil)))

(defn make-test-renderer [{:keys [with-websockets?] :or {with-websockets? true}}]
  (map->TestRenderer
    {:with-websockets? with-websockets?
     :runner-atom      (atom nil)
     :root             TestReport
     :target           "fulcro-spec-report"}))
