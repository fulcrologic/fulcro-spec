(defproject fulcrologic/fulcro-spec "3.0.1"
  :description "Helper Macros for clj and cljs test"
  :url "https://github.com/fulcrologic/fulcro-spec"
  :license {:name "MIT Public License"
            :url  "https://opensource.org/licenses/MIT"}
  :plugins [[lein-tools-deps "0.4.1"]]

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}

  :jar-exclusions [#"public/.*" #"^workspaces/.*" #"\.DS_Store"]
  :source-paths ["src/main"]
  :test-paths ["src/test"])
