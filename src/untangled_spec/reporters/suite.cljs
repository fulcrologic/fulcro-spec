(ns ^:figwheel-always untangled-spec.reporters.suite
  (:require
    [bidi.bidi :as bidi]
    [cljs.test]
    [figwheel.client.heads-up :as fig]
    [goog.dom :as gdom]
    [goog.object]
    [om.next :as om]
    [pushy.core :as pushy]
    [untangled-spec.reporters.browser :as browser]
    [untangled-spec.reporters.impl.base-reporter :as base]))

(enable-console-print!)

(defn get-element-or-else [id else]
  (or (gdom/getElement id)
      (else id)))

(defn render-tests [{:keys [reconciler renderer dom-target]}]
  (om/add-root! reconciler renderer
    (get-element-or-else
      dom-target
      #(doto (str "TestSuite rendering failed to find element with id: '" % "'")
         js/console.error
         js/alert))))

(defn om-read [{:keys [state ast query]} k _]
  {:value (case (:type ast) :prop (get @state k)
            (om/db->tree query (get @state k) @state))})

(defmulti om-write om/dispatch)
(defmethod om-write `set-filter [{:keys [state]} _ {:keys [new-filter]}]
  (swap! state assoc :report/filter new-filter))

(def test-parser (om/parser {:read om-read :mutate om-write}))

(def app-routes
  ["" (into {}
        (map (juxt name identity)
          (keys browser/filters)))])

(defn set-page! [reconciler]
  (fn [new-filter]
    (om/transact! reconciler
      `[(set-filter ~{:new-filter new-filter})])))

(defn new-test-suite [target]
  (let [state (atom {:top (base/make-testreport)
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
    {:state      state
     :path       (atom [:top])
     :reconciler reconciler
     :renderer   browser/TestReport
     :dom-target target}))
