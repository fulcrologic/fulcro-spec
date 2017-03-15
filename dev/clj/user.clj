(ns clj.user
  (:require
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [com.stuartsierra.component :as cp]
    [figwheel-sidecar.system :as fsys]
    [untangled-spec.impl.runner :as ir]
    [untangled-spec.suite :as suite]
    [untangled-spec.selectors :as sel]))

(defn start-figwheel
  "Start Figwheel on the given builds, or defaults to build-ids in `figwheel-config`."
  ([]
   (let [props (System/getProperties)
         figwheel-config (fsys/fetch-config)
         all-builds (->> figwheel-config :data :all-builds (mapv :id))]
     (start-figwheel (keys (select-keys props all-builds)))))
  ([build-ids]
   (let [figwheel-config (fsys/fetch-config)
         target-config (-> figwheel-config
                         (assoc-in [:data :build-ids]
                           (or (seq build-ids)
                               (-> figwheel-config :data :build-ids))))]
     (-> (cp/system-map
           :css-watcher (fsys/css-watcher {:watch-paths ["resources/public/css"]})
           :figwheel-system (fsys/figwheel-system target-config))
       cp/start :figwheel-system fsys/cljs-repl))))

;; WARNING: INTERNAL syntax ONLY, you should not need to use this yourself
;; instead use (suite/def-test-suite start-tests ..opts.. ..selectors..)
(defn start []
  (reset! ir/runner
    (suite/test-suite-internal
      {:config {:port 8888}
       :source-paths ["src" "dev"]
       :test-paths ["test"]}
      {:default #{::sel/none :focused}
       :available #{:focused :should-fail}})))

(defn stop []
  (swap! ir/runner cp/stop)
  (reset! ir/runner {}))

(defn reset []
  (stop) (tools-ns-repl/refresh :after 'clj.user/start))

(defn engage [& build-ids]
  (start) (start-figwheel build-ids))
