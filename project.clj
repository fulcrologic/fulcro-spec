(defproject smooth-test "0.1.0-SNAPSHOT"
  :description "Smooth testing"
  :url ""
  :license {:name "MIT Public License"
            :url ""}
  :dependencies [
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 ]
  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-figwheel "0.3.7"]]
  :clean-targets [:target-path "target" "resources/public/js"]
  :cljsbuild {
              :builds [
                       { :id "dev"
                        :source-paths ["src" "test"]
                        :compiler {:main smooth-test.behavior-spec
                                   :asset-path "js/out"
                                   :output-to  "resources/public/js/main.js"
                                   :output-dir "resources/public/js/out"} 
                        :figwheel true
                        }
                       ]
              }
  :figwheel {
             :nrepl-port 7888
             }
  :profiles {
             :dev {
                   :dependencies [
                                  [midje "1.7.0"]
                                  ]
                   :source-paths ["src" "test" "dev"]
                   }
             }
  )
