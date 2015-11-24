(defproject untangled-spec "0.2.1"
  :description "A Behavioral specification system for clj and cljs stacked on clojure.test"
  :url ""
  :license {:name "MIT Public License"
            :url ""}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.48"]
                 [colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [cljsjs/react-with-addons "0.14.0-1" :scope "test"]
                 [org.omcljs/om "1.0.0-alpha22"]
                 [io.aviso/pretty "0.1.19"]
                 ]
  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]]
  :clean-targets ^{:protect false} [:target-path "target" "resources/public/js"]
  :cljsbuild {
              :builds [
                       {:id "test"
                        :source-paths ["src" "dev" "test"]
                        :figwheel { :on-jsload "cljs.user/on-load" }
                        :compiler {:main cljs.user
                                   :output-to "resources/public/js/test/test.js"
                                   :output-dir "resources/public/js/test/out"
                                   :recompile-dependents true
                                   :asset-path "js/test/out"
                                   :optimizations :none
                                   }
                        }
                       ]
              }
  :figwheel {
             :nrepl-port 7888
             }
  :profiles {
             :dev {
                   :source-paths ["src" "test" "dev"]
                   :repl-options {
                                  :init-ns clj.user
                                  :port 7001
                                  }
                   :env {:dev true }
                   }
             }
  :test-refresh  {:report  untangled-spec.report/untangled-report}
  )
