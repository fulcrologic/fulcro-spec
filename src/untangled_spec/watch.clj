(ns untangled-spec.watch
  (:require
    [clojure.tools.namespace.dir :as tools-ns-dir]
    [clojure.tools.namespace.find :refer [clj]]
    [clojure.tools.namespace.track :as tools-ns-track]
    [clojure.tools.namespace.repl :as tools-ns-repl]
    [com.stuartsierra.component :as cp]))

(defn- make-change-tracker [watch-dirs]
  (tools-ns-dir/scan-dirs (tools-ns-track/tracker) watch-dirs {:platform clj}))

(defn- scan-for-changes [tracker watch-dirs]
  (try (let [new-tracker (tools-ns-dir/scan-dirs tracker watch-dirs {:platform clj})]
         new-tracker)
    (catch Exception e
      (.printStackTrace e)
      ;; return the same tracker so we dont try to run tests
      tracker)))

(defmacro async [& body]
  `(let [ns# *ns*]
     (.start
       (Thread.
         (fn []
           (binding [*ns* ns#]
             ~@body))))))

(defn something-changed? [new-tracker curr-tracker]
  (not= new-tracker curr-tracker))

(defrecord ChangeListener [watching? watch-dirs run-tests]
  cp/Lifecycle
  (start [this]
    (apply tools-ns-repl/set-refresh-dirs watch-dirs)
    (async
      (loop [tracker (make-change-tracker watch-dirs)]
        (let [new-tracker (scan-for-changes tracker watch-dirs)]
          (when @watching?
            (when (something-changed? new-tracker tracker)
              (try (run-tests (:test/runner this) {:refresh? true})
                (catch Exception e (.printStackTrace e))))
            (do (Thread/sleep 200)
              (recur (dissoc new-tracker
                             ::tools-ns-track/load
                             ::tools-ns-track/unload)))))))
    this)
  (stop [this]
    (reset! watching? false)
    this))

(defn on-change-listener [{:keys [source-paths test-paths]} run-tests]
  (cp/using
    (map->ChangeListener
      {:watching? (atom true)
       :watch-dirs (concat source-paths test-paths)
       :run-tests run-tests})
    [:test/runner]))
