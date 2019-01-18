(defproject fulcrologic/fulcro-spec "3.0.0-SNAPSHOT"
  :description "Helper Macros for clj and cljs test"
  :url "https://github.com/fulcrologic/fulcro-spec"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :dependencies [[colorize "0.1.1" :exclusions [org.clojure/clojure]]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [fulcrologic/fulcro "2.6.7"]
                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.439" :scope "provided"]]

  :source-paths ["src"]
  :test-paths ["test"])
