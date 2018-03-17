(ns fulcro-spec.suite
  #?(:cljs (:require-macros [fulcro-spec.suite]))
  (:require
    [clojure.spec.alpha :as s]
    [com.stuartsierra.component :as cp]
    [fulcro-spec.runner :as runner]
    [fulcro-spec.selectors :as sel]
    [fulcro-spec.spec :as fss]
    #?@(:cljs ([fulcro-spec.renderer :as renderer]
               [fulcro-spec.router :as router]))
    #?@(:clj ([clojure.java.io :as io]
              [clojure.tools.namespace.find :as tools-ns-find]
              [clojure.tools.namespace.repl :as tools-ns-repl]
              [fulcro-spec.impl.macros :as im]
              [fulcro-spec.impl.runner :as ir]))))

#?(:cljs
   (defn test-renderer
     "FOR INTERNAL (DEV TIME) UNTANGLED_SPEC USE ONLY

      WARNING: You should not need to use this directly, instead you should be starting a server `def-test-suite`
      and going to `localhost:PORT/fulcro-spec-server-tests.html`.

      Creates a renderer for server (clojure) tests when using `def-test-suite`."
     [& [opts]]
     (cp/start
       (cp/system-map
         :test/renderer (renderer/make-test-renderer opts)
         :test/router (router/make-router)))))

#?(:clj
   (defn- make-test-fn [env opts]
     `(fn []
        ~(if (im/cljs-env? env)
           `(cljs.test/run-all-tests ~(:ns-regex opts)
              (cljs.test/empty-env ::TestRunner))
           `(let [test-nss#
                  (mapcat (comp tools-ns-find/find-namespaces-in-dir io/file)
                    ~(:test-paths opts))]
              (apply require test-nss#)
              (apply clojure.test/run-tests test-nss#))))))

#?(:clj
   (defmacro def-test-suite
     "For use in defining a fulcro-spec test suite.
      ARGUMENTS:
      * `suite-name` is the name (symbol) of the test suite function you can call, see each section below for details.
      * `opts` map containing configuration for the test suite, see each section below for details.
      * `selectors` contains `:available` and `:default` sets of keyword selectors for your tests. See `fulcro-spec.selectors` for more info.

      CLOJURESCRIPT:
      * Make sure you are defining a cljsbuild that emits your client tests to `js/test/test.js`.
      * You can call the function `suite-name` repeatedly, it will just re-run the tests.
      * `opts` should contain :
      ** `:ns-regex` that will be used to filter to just your test namespaces.

      CLOJURE:
      * Defines a function `suite-name` that restarts the webserver, presumably with new configuration.
      * Starts a webserver that serves `localhost:PORT/fulcro-spec-server-tests.html`, (see `opts` description)
      * `opts` should contain :
      ** `:test-paths` : used to find your test files.
      ** `:source-paths` : concatenated with `test-paths` to create watch and refresh dirs, so that we can scan those directories for changes and refresh those namespaces whenever they change.
      ** `:config` : should contain any necessary configuration for the webserver, should just be `:port` (eg: {:config {:port 8888}})
      "
     [suite-name opts selectors]
     (if (im/cljs-env? &env)
       ;;BROWSER
       `(let [test!# ~(make-test-fn &env opts)]
          (defonce _# (sel/initialize-selectors! ~selectors))
          (defonce renderer# (test-renderer {:with-websockets? false}))
          (def test-system# (runner/test-runner ~opts test!# renderer#))
          (defn ~suite-name []
            (runner/run-tests (:test/runner test-system#) {})))
       ;;SERVER
       `(let [test!# ~(make-test-fn &env opts)]
          (sel/initialize-selectors! ~selectors)
          (defn stop# [] (swap! ir/runner (comp (constantly {}) cp/stop)))
          (defn start# [] (reset! ir/runner (runner/test-runner ~opts test!#)))
          (defn ~suite-name [] (stop#) (start#))))))

#?(:clj
   (defmacro test-suite-internal
     "FOR INTERNAL (DEV TIME) USE ONLY

      WARNING: You should not need to use this directly,
      instead you should be starting a server `def-test-suite`
      and going to `localhost:PORT/fulcro-spec-server-tests.html`.

      Creates a webserver for clojure tests, can be `start` and `stop` -ed.
      This is in case you need to refresh your namespaces in your server restarts,
      however this should only be necessary when developing fulcro-spec itself,
      as once it's in a jar it can't be refreshed."
     [opts selectors]
     `(let [test!# ~(make-test-fn &env opts)]
        (sel/initialize-selectors! ~selectors)
        (runner/test-runner ~opts test!#))))
