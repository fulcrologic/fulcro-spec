(defproject fulcrologic/fulcro-spec "2.1.1"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url "https://github.com/fulcrologic/fulcro-spec"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [kibu/pushy "0.3.8"]
                 [lein-doo "0.1.10" :scope "test"]
                 [fulcrologic/fulcro "2.4.3"]

                 [http-kit "2.2.0"]
                 [ring/ring-core "1.6.3" :exclusions [commons-codec]]
                 [bk/ring-gzip "0.2.1"]
                 [bidi "2.1.3"]
                 [com.taoensso/sente "1.12.0" :exclusions [org.clojure/tools.reader]]

                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/tools.namespace "0.3.0-alpha4"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.21.1"]
            [lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]                             ;; for cljs CI tests
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

  :jvm-opts ~(let [version (System/getProperty "java.version")
                   base-options ["-XX:-OmitStackTraceInFastThrow"]
                   [major & _] (clojure.string/split version #"\.")]
               (if (>= (Integer/parseInt major) 9)
                 (conj base-options "--add-modules" "java.xml.bind")
                 base-options))

  :figwheel {:nrepl-port  7888
             :server-port 3457}

  :aliases {"jar"       ["with-profile" "with-cljs" "jar"]
            "test-cljs" ["with-profile" "test" "doo" "firefox" "automated-tests" "once"]
            "clojars"   ["with-profile" "with-cljs" "deploy" "clojars"]}

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
                                        [figwheel-sidecar "0.5.14" :exclusions [ring/ring-core http-kit joda-time]]
                                        [org.clojure/tools.nrepl "0.2.13"]
                                        [org.clojure/test.check "0.9.0"]]}})
