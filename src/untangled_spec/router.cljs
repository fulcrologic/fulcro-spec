(ns untangled-spec.router
  (:require
    [clojure.set :as set]
    [com.stuartsierra.component :as cp]
    [om.next :as om]
    [pushy.core :as pushy]
    [untangled-spec.renderer :as renderer]
    [untangled-spec.selectors :as sel]
    [untangled.client.mutations :as m])
  (:import
    (goog.Uri QueryData)))

(defn parse-fragment [path]
  (let [data (new QueryData path)]
    {:filter (some-> (.get data "filter") keyword)
     :selectors (some->> (.get data "selectors") sel/parse-selectors)}))

(defn update-fragment! [history k f & args]
  (let [data (new goog.Uri.QueryData (pushy/get-token history))]
    (.set data (name k) (apply f (.get data (name k)) args))
    (pushy/set-token! history
      ;; so we dont get an ugly escaped url
      (.toDecodedString data))))

(defn assoc-fragment! [history k v]
  (update-fragment! history k (constantly v)))

(defn new-history [parser tx!]
  (let [history (with-redefs [pushy/update-history!
                              #(doto %
                                 (.setUseFragment true)
                                 (.setPathPrefix "")
                                 (.setEnabled true))]
                  (pushy/pushy tx! parser))]
    history))

(defmethod m/mutate `set-page-filter [{:keys [state ast]} k {:keys [filter]}]
  {:action #(swap! state assoc :ui/current-filter
              (or (and (nil? filter) :all)
                  (and (contains? renderer/filters filter) filter)
                  (do (js/console.warn "INVALID FILTER: " (str filter)) :all)))})

(defn- update-some [m k f & more]
  (update m k (fn [v] (if-not v v (apply f v more)))))

(defmethod m/mutate `sel/set-active-selectors [{:keys [state ast]} k {:keys [selectors]}]
  {:action #(swap! state update-some :selectors sel/set-selectors* selectors)
   :remote true})

(defrecord Router []
  cp/Lifecycle
  (start [this]
    (let [{:keys [reconciler]} (-> this :test/renderer :app)
          history (new-history parse-fragment
                    (fn on-route-change [{:keys [filter selectors]}]
                      (when filter
                        (om/transact! reconciler
                          `[(set-page-filter
                              ~{:filter filter})]))
                      (om/transact! reconciler
                        `[(sel/set-active-selectors
                            ~{:selectors selectors})])))]
      (defmethod m/mutate `renderer/toggle-filter [{:keys [state]} _ {:keys [filter]}]
        {:action #(update-fragment! history :filter
                    (let [new-filter (name filter)]
                      (fn [old-filter]
                        (if (= old-filter new-filter)
                          "all" new-filter))))})
      (defmethod m/mutate `sel/set-selector [{:keys [state]} _ new-selector]
        {:action #(assoc-fragment! history :selectors (sel/to-string (sel/set-selector* (:selectors @state) new-selector)))})
      (defmethod m/mutate `sel/set-selectors [{:keys [state ast]} k {:keys [selectors]}]
        {:action #(assoc-fragment! history :selectors (or selectors (sel/to-string (:selectors @state))))})
      (pushy/start! history)
      this))
  (stop [this]
    (remove-method m/mutate `renderer/set-filter)
    (remove-method m/mutate `sel/set-selector)
    (remove-method m/mutate `sel/set-selectors)
    this))

(defn make-router []
  (cp/using
    (map->Router {})
    [:test/renderer]))
