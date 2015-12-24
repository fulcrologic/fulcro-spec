(defproject untangled-spec "0.2.5-SNAPSHOT"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url ""
  :license {:name "MIT Public License"
            :url  ""}
  :dependencies [
                 [org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.170" :scope "provided"]
                 [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [cljsjs/react-with-addons "0.14.0-1"]
                 [org.omcljs/om "1.0.0-alpha22" :scope "provided"]
                 [io.aviso/pretty "0.1.19"]
                 ]
  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.5.0-2" :exclusions [ring/ring-core commons-fileupload clj-time joda-time org.clojure/clojure org.clojure/tools.reader]]]

  :repositories [["releases" "https://artifacts.buehner-fry.com/artifactory/internal-release"]
                 ["third-party" "https://artifacts.buehner-fry.com/artifactory/internal-3rdparty"]]

  :deploy-repositories [["releases" {:url           "https://artifacts.buehner-fry.com/artifactory/internal-release"
                                     :snapshots     false
                                     :sign-releases false}]
                        ["snapshots" {:url           "https://artifacts.buehner-fry.com/artifactory/internal-snapshots"
                                      :sign-releases false}]]

  :clean-targets ^{:protect false} [:target-path "target" "resources/public/js"]
  :cljsbuild {
              :builds [
                       {:id           "test"
                        :source-paths ["src" "dev" "test"]
                        :figwheel     {:on-jsload "cljs.user/on-load"}
                        :compiler     {:main                 cljs.user
                                       :output-to            "resources/public/js/test/test.js"
                                       :output-dir           "resources/public/js/test/out"
                                       :recompile-dependents true
                                       :asset-path           "js/test/out"
                                       :optimizations        :none
                                       }
                        }]}
  :figwheel {:nrepl-port 7888}
  :profiles {
             :dev {
                   :source-paths ["src" "test" "dev"]
                   :repl-options {
                                  :init-ns clj.user
                                  :port    7001
                                  }
                   :env          {:dev true}
                   }}
  :test-refresh {:report untangled-spec.report/untangled-report}
  )
