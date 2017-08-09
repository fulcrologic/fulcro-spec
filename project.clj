(defproject fulcrologic/fulcro-spec "1.0.0-beta7-SNAPSHOT"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url ""
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.taoensso/timbre "4.10.0"]
                 [kibu/pushy "0.3.7"]
                 [lein-doo "0.1.7" :scope "test"]
                 [ring/ring "1.6.2" :exclusions [commons-codec]]
                 [fulcrologic/fulcro "1.0.0-beta7-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.854"]
                 [org.clojure/tools.namespace "0.3.0-alpha4"]
                 [clojure-future-spec "1.9.0-alpha17"]
                 [org.omcljs/om "1.0.0-beta1"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.19.0" :exclusions [org.clojure/tools.namespace]]
            [lein-cljsbuild "1.1.6"]
            [lein-doo "0.1.7"]                              ;; for cljs CI tests
            [lein-shell "0.5.0"]]

  :release-tasks [["shell" "bin/release" "all_tasks"]]
  :jar-exclusions [#".*/index.html"]

  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["resources"]

  ;; this for backwards compatability, should now use fulcro-spec.suite/def-test-suite
  ;; (see dev/clj/user.clj for an example)
  :test-refresh {:report       fulcro-spec.reporters.terminal/fulcro-report
                 :changes-only true
                 :with-repl    true}
  :test-selectors {:default (complement :should-fail)}

  ;; CI tests: Set up to support karma runner. Recommend running against chrome. See README
  :doo {:build "automated-tests"
        :paths {:karma "node_modules/karma/bin/karma"}}

  :clean-targets ^{:protect false} [:target-path "target" "resources/public/js" "resources/private/js"]

  :cljsbuild {:builds {;; For rendering specs without figwheel (eg: server side tests)
                       :spec-renderer {:source-paths ["src"]
                                       :compiler     {:main          fulcro-spec.spec-renderer
                                                      :output-to     "resources/public/js/test/fulcro-spec-renderer.js"
                                                      :output-dir    "target/js"
                                                      :asset-path    "js/test/fulcro-spec-renderer"
                                                      :optimizations :simple}}}}

  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]

  :figwheel {:nrepl-port  7888
             :server-port 3457}

  :aliases {"jar"       ["with-profile" "with-cljs" "jar"]
            "test-cljs" ["with-profile" "test" "doo" "firefox" "automated-tests" "once"]
            "test-clj"  ["test-refresh" ":run-once"]}

  :profiles {:with-cljs {:prep-tasks ["compile" ["cljsbuild" "once" "spec-renderer"]]}
             :test      {:cljsbuild {:builds {:automated-tests {:doc          "For CI tests. Runs via doo"
                                                                :source-paths ["src" "test"]
                                                                :compiler     {:output-to     "resources/private/js/unit-tests.js"
                                                                               :output-dir    "resources/private/js/unit-tests"
                                                                               :asset-path    "js/unit-tests"
                                                                               :main          fulcro-spec.all-tests
                                                                               :optimizations :whitespace}}}}}
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
                         :dependencies [[com.cemerick/piggieback "0.2.2"]
                                        [figwheel-sidecar "0.5.12" :exclusions [ring/ring-core http-kit joda-time]]
                                        [org.clojure/tools.nrepl "0.2.13"]
                                        [org.clojure/test.check "0.9.0"]]}})
