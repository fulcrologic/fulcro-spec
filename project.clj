(defproject navis/untangled-spec "1.0.0-alpha2"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url ""
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.taoensso/timbre "4.8.0"]
                 [kibu/pushy "0.3.6"]
                 [lein-doo "0.1.6" :scope "test"]
                 [navis/untangled-client "0.8.0"]
                 [navis/untangled-server "0.7.0" :exclusions [com.taoensso/timbre org.clojure/java.classpath]]
                 [navis/untangled-ui "1.0.0-alpha1"]
                 [navis/untangled-websockets "0.3.3"]
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/clojurescript "1.9.473"]
                 [org.clojure/tools.namespace "0.3.0-alpha3"]
                 [org.omcljs/om "1.0.0-alpha48"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.19.0" :exclusions [org.clojure/tools.namespace]]
            [lein-cljsbuild "1.1.5"]
            [lein-doo "0.1.6"]                              ;; for cljs CI tests
            [lein-shell "0.5.0"]]

  :release-tasks [["shell" "bin/release" "all_tasks"]]

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]

  ;; this for backwards compatability, should now use untangled-spec.suite/def-test-suite
  ;; (see dev/clj/user.clj for an example)
  :test-refresh {:report       untangled-spec.reporters.terminal/untangled-report
                 :changes-only true
                 :with-repl    true}
  :test-selectors {:default (complement :should-fail)}

  ;; CI tests: Set up to support karma runner. Recommend running against chrome. See README
  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}

  :clean-targets ^{:protect false} [:target-path "target" "resources/public/js" "resources/private/js"]

  :cljsbuild {:builds        {;; For rendering specs without figwheel (eg: server side tests)
                              :spec-renderer {:source-paths ["src"]
                                              :compiler     {:main          untangled-spec.spec-renderer
                                                             :output-to     "resources/public/js/test/untangled-spec-renderer.js"
                                                             :output-dir    "resources/public/js/test/untangled-spec-renderer"
                                                             :asset-path    "js/test/untangled-spec-renderer"
                                                             :optimizations :simple}}}}

  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]

  :figwheel {:nrepl-port  7888
             :server-port 3457}

  :aliases {"jar"               ["with-profile" "with-cljs" "jar"]
            "test-cljs"         ["with-profile" "test" "doo" "phantom" "automated-tests" "once"]
            "test-clj"          ["test-refresh" ":run-once"]}

  :profiles {:with-cljs {:prep-tasks ["compile" ["cljsbuild" "once" "spec-renderer"]]}
             :test      {:cljsbuild {:builds {:automated-tests {:doc          "For CI tests. Runs via doo"
                                                                :source-paths ["src" "test"]
                                                                :compiler     {:output-to     "resources/private/js/unit-tests.js"
                                                                               :output-dir    "resources/private/js/unit-tests"
                                                                               :asset-path    "js/unit-tests"
                                                                               :main          untangled-spec.all-tests
                                                                               :optimizations :simple}}}}}
             :dev       {:cljsbuild    {:builds {:test {:source-paths ["src" "dev" "test"]
                                                        :figwheel     {:on-jsload cljs.user/on-load}
                                                        :compiler     {:main          cljs.user
                                                                       :output-to     "resources/public/js/test/test.js"
                                                                       :output-dir    "resources/public/js/test/out"
                                                                       :asset-path    "js/test/out"
                                                                       :optimizations :none}}}}
                         :source-paths ["src" "test" "dev"]
                         :repl-options {:init-ns          clj.user
                                        :port             7007
                                        :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                         :dependencies [[com.cemerick/piggieback "0.2.1"]
                                        [figwheel-sidecar "0.5.8" :exclusions [ring/ring-core http-kit joda-time]]
                                        [org.clojure/tools.nrepl "0.2.12"]
                                        [org.clojure/test.check "0.9.0"]]}})
