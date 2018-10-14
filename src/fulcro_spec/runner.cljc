(ns fulcro-spec.runner
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :as t]
    [cljs.test #?@(:cljs (:include-macros true))]
    [com.stuartsierra.component :as cp]
    [fulcro-spec.assertions :as ae]
    [fulcro-spec.reporter :as reporter]
    [fulcro-spec.selectors :as sel]
    [fulcro-spec.spec :as fss]
    [fulcro.client.primitives :as prim]
    #?@(:cljs ([fulcro.client.mutations :as m]
                [fulcro-spec.renderer :as renderer]
                [fulcro-spec.router :as router]))
    #?@(:clj (
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [clojure.walk :as walk]
    [cognitect.transit :as transit]
    [fulcro.server :as oms]
    [ring.util.response :as resp]
    [fulcro-spec.impl.macros :as im]
    [fulcro-spec.watch :as watch]
    [fulcro.easy-server :as fsy]
    [fulcro.websockets.protocols :as ws]
    [fulcro.websockets.components.channel-server :as wcs]))))


#?(:clj
   (defmethod print-method Throwable [e w]
     (.write w (str e))))

#?(:clj
   (defn- ensure-encodable [tr]
     (letfn [(encodable? [x]
               (some #(% x)
                 [number? string? symbol? keyword? sequential?
                  (every-pred map? (comp not record?))]))]
       (walk/postwalk #(cond-> % (not (encodable? %)) pr-str) tr))))

#?(:clj
   (defn- send-renderer-msg
     ([system k edn cid]
      (let [cs (:channel-server system)]
        (ws/push cs cid k
          (ensure-encodable edn))))
     ([system k edn]
      (->> system :channel-server
        :connected-cids deref :any
        (mapv (partial send-renderer-msg system k edn))))))

#?(:clj
   (defrecord ChannelListener [channel-server]
     ws/WSListener
     (client-dropped [this cs cid] this)
     (client-added [this cs cid] this)
     cp/Lifecycle
     (start [this]
       (wcs/add-listener wcs/listeners this)
       this)
     (stop [this]
       (wcs/remove-listener wcs/listeners this)
       this)))

#?(:clj
   (defn- make-channel-listener []
     (cp/using
       (map->ChannelListener {})
       [:channel-server :test/reporter])))

(defn- novelty! [system mut-key novelty]
  #?(:cljs (let [reconciler (get-in system [:test/renderer :test/renderer :app :reconciler])]
             (prim/transact! reconciler `[(~mut-key ~novelty)])
             (prim/force-root-render! reconciler))
     :clj  (send-renderer-msg system mut-key novelty)))

(defn- render-tests [{:keys [test/reporter] :as runner}]
  (novelty! runner 'fulcro-spec.renderer/render-tests
    (reporter/get-test-report reporter))
  runner)

(defn run-tests [runner {:keys [refresh?] :or {refresh? false}}]
  (reporter/reset-test-report! (:test/reporter runner))
  (let [result #?(:cljs :ok :clj (if refresh? (tools-ns-repl/refresh) :ok))]
    (if (not= :ok result)
      (do                                                   ;; CLJ only
        (novelty! runner 'fulcro-spec.renderer/show-compile-error result)
        (println "Refresh failed: " result))
      (reporter/with-fulcro-reporting
        runner render-tests
        ((:test! runner))))))

(defrecord TestRunner [opts]
  cp/Lifecycle
  (start [this]
    #?(:cljs (let [runner-atom (-> this :test/renderer :test/renderer :runner-atom)]
               (reset! runner-atom this)))
    this)
  (stop [this]
    this))

(defn- make-test-runner [opts test! & [extra]]
  (cp/using
    (merge (map->TestRunner {:opts opts :test! test!})
      extra)
    [:test/reporter #?(:clj :channel-server)]))

(s/def ::test-paths (s/coll-of string?))
(s/def ::source-paths (s/coll-of string?))
(s/def ::ns-regex ::fss/regex)
(s/def ::port number?)
(s/def ::config (s/keys :req-un [::port]))
(s/def ::opts (s/keys :req-un [#?@(:cljs [::ns-regex])
                               #?@(:clj [::source-paths ::test-paths ::config])]))
(s/fdef test-runner
  :args (s/cat
          :opts ::opts
          :test! fn?
          :renderer (s/? any?)))
(defn test-runner [opts test! & [renderer]]
  #?(:cljs (cp/start
             (cp/system-map
               :test/runner (make-test-runner opts test!
                              {:test/renderer renderer
                               :read          (fn [runner k params]
                                                {:value
                                                 (case k
                                                   :selectors (sel/get-current-selectors)
                                                   (prn ::read k params))})
                               :mutate        (fn [runner k params]
                                                {:action
                                                 #(condp = k
                                                    `sel/set-selector
                                                    #_=> (do
                                                           (sel/set-selector! params)
                                                           (run-tests runner {}))
                                                    `sel/set-active-selectors
                                                    #_=> (do
                                                           (sel/set-selectors! (:selectors params))
                                                           (run-tests runner {}))
                                                    (prn ::mutate k params))})})
               :test/reporter (reporter/make-test-reporter)))
     :clj  (let [system     (atom nil)
                 api-read   (fn [env k params]
                              {:value
                               (case k
                                 :selectors (sel/get-current-selectors)
                                 (prn ::read k params))})
                 api-mutate (fn [env k params]
                              {:action
                               #(condp = k
                                  `sel/set-selector
                                  #_=> (do
                                         (sel/set-selector! params)
                                         (run-tests (:test/runner @system) {}))
                                  `sel/set-active-selectors
                                  #_=> (do
                                         (sel/set-selectors! (:selectors params))
                                         (run-tests (:test/runner @system) {}))
                                  (prn ::mutate k params))})]
             (reset! system
               (cp/start
                 (fsy/make-fulcro-server
                   :parser (oms/parser {:read api-read :mutate api-mutate})
                   :components {:config           {:value (:config opts)}
                                :channel-server   (wcs/make-channel-server)
                                :channel-listener (make-channel-listener)
                                :test/runner      (make-test-runner opts test!)
                                :test/reporter    (reporter/make-test-reporter)
                                :change/watcher   (watch/on-change-listener opts run-tests)}
                   :extra-routes {:routes   ["/" {"_fulcro_spec_chsk"             :web-socket
                                                  "fulcro-spec-server-tests.html" :server-tests}]
                                  :handlers {:web-socket   wcs/route-handlers
                                             :server-tests (fn [{:keys [request]} _match]
                                                             (resp/resource-response "fulcro-spec-server-tests.html"
                                                               {:root "public"}))}}))))))
