(ns untangled-spec.reporters.impl.terminal
  (:require
    [untangled-spec.reporters.impl.base-reporter :as impl]))

(def ^:dynamic *test-state* (atom (impl/make-testreport)))
(def ^:dynamic *test-scope* (atom []))

(defn push-test-scope [test-item index]
  (swap! *test-scope* conj :test-items index))

(defn pop-test-scope []
  (swap! *test-scope* (apply comp (repeat 2 pop))))

(defn set-test-result [status]
  (impl/set-test-result *test-state* @*test-scope* status))

(defn begin-namespace [name]
  (let [namespaces (get @*test-state* :namespaces)
        name-space-location (impl/get-namespace-location namespaces name)]
    (reset! *test-scope* [:namespaces name-space-location])
    (swap! *test-state* #(assoc-in % [:namespaces name-space-location]
                                   (impl/make-tests-by-namespace name)))))

(defn end-namespace [] (pop-test-scope))

(defn begin-specification [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-specification [] (pop-test-scope))

(defn begin-behavior [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-behavior [] (pop-test-scope))

(defn begin-provided [x]
  (let [[test-item test-items-count]
        (impl/begin x *test-state* @*test-scope*)]
    (push-test-scope test-item test-items-count)))

(defn end-provided [] (pop-test-scope))

(defn pass [] (set-test-result :passed))

(defn error [detail]
  (impl/error detail *test-state* @*test-scope*))

(defn fail [detail]
  (impl/fail detail *test-state* @*test-scope*))

(defn summary [stats]
  (impl/summary stats @*test-scope* *test-state*))
