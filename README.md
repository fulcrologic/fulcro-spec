# untangled-spec

A Specification testing framework.

DEVELOPMENT NOTES:

To run cljs tests:

     lein figwheel
     
To run clj tests:

     lein test-refresh

## CI Testing

To run the CLJ and CLJS tests on the CI server, it must have chrome, node, and npm installed. Then
you can simply use the Makefile:

    make tests

## Usage

```
   [navis/untangled-spec "0.3.8"]
```

The macros in untangled spec wrap clojure/cljs test, so that you may
use any of the features of the core library. The specification DSL makes it much easier to read the
tests, and also includes a number of useful features:

- Outline rendering
- Left-to-right assertions
- More readable output, such as data structure comparisons on failure (with diff notation as well)
- Real-time refresh of tests on save (client and server)
- Seeing test results in any number of browsers at once
- Mocking of normal functions, including native javascript (but as expected: not macros or inline functions)
    - Mocking verifies call sequence and call count
    - Mocks can easily verify arguments received
    - Mocks can simulate timelines for CSP logic
- Protocol testing support (helps prove network interactions are correct without running the full stack)

## Setting up

Please use the Untangled Tutorial or TodoMVC projects as samples for setting up a project. 

In the [tutorial](https://github.com/untangled-web/untangled-tutorial): If you look in `test/client/app` 
you'll see a few files. Only one of the four is a specification. The other three
serve the following purposes:

- `all_tests.cljs` : An entry point for CI testing from the command line.
- `suite.cljs` : The entry point for browser test rendering.
- `tests_to_run.cljs` : A file that does nothing more than require all of the specs. The test runners search for
testing namespaces, so if you don't load them somewhere, they won't be found. Since there are two places tests
run from (browser and CI) it makes sense to make this DRY.

There is a `package.json` file for installing node packages to run CI tests.
The `project.clj` includes various things to make all of this work:

- The lein doo plugin, for running tests through karma *via* node (in Chrome).
- A `:doo` section to configure the CI runner
- A cljsbuild for test with figwheel true. This is the browser test build.
- A cljsbuild for the CI tests output (automated-tests).
- The lein `test-refresh` plugin, which will re-run server tests on save, and also can be configured with the
spec renderer (see the `:test-refresh` section in the project file).

## Running server tests

See `test/server/app/server_spec.clj` for a sample specification (again, on the Tutorial project). To run all specs, just use:

```
lein test-refresh
```

## Running client tests (during development)

NOTE: This assumes you're playing with the Tutorial project.

Just include `-Dtest` in your JVM argument list. This will cause the test build to start running via figwheel. Then
just open the [http://localhost:3449/test.html](http://localhost:3449/test.html) file in your browser.

## Anatomy of a specification

The main macros are `specification`, `behavoir`, and `assertions`:

```
(specification "A Thing"
   (behavior "does something"
      (assertions
         "optional sub-clause"
         form => expected-form
         form2 => expected-form2

         "subclause"
         form => expected-form)))
```

The specification macro just outputs a `deftest`, so you are free to use `is`, `are`, etc. The `behavior` macro
outputs additional events for the renderer to make an outline.

### Assertions

Special arrows:

- `=fn=>` : The right-hand side should be a boolean-returning lambda: (fn [v] boolean)

### Mocking

The mocking system does a lot in a very small space. It can be invoked via the `provided` or `when-mocking` macro.
The former requires a string and adds an outline section. The latter does not change the outline output.

Mocking must be done in the context of a specification, and creates a scope for all sub-outlines. Generally
you want to isolate mocking to a specific behavior:

```
(specification "Thing"
  (behavior "Does something"
    (when-mocking
      (my-function arg1 arg2) => (do (assertions
                                        arg1 => 3
                                        arg2 => 5)
                                     true)

      (my-function 3 5))))
```

Basically, you include triples (a form, arrow, form), followed by the code to execute (which will not have arrows).

It is important to note that the mocking support does a bunch of verification at the end of your test:

- It verifies that your functions are called the appropriate number of times (at least once is the default)
- It uses the mocked functions in the order specified.
- It captures the arguments in the symbols you provide (in this case arg1 and arg2). These
are available for use in the RHS of the mock expression.
- It returns whatever the RHS of the mock expression indicates
- If assertions run in the RHS form, they will be honored (for test failures)

So, the following mock script:

```
(when-mocking
   (f a) =1x=> a
   (f a) =2x=> (+ 1 a)
   (g a b) => 32

   (assertions
     (+ (f 2) (f 2) (f 2) (g 1 2) (g 99 4) => 72))
```

should pass. The first call to `f` returns the argument. The next two calls return the argument plus one.
`g` can be called any amount (but at least once) and returns 32 each time.

If you were to remove any call to `f` this test would fail.

#### Timeline testing

On occasion you'd like to mock things that use callbacks. Chains of callbacks can be a challenge to test, especially
when you're trying to simulate timing issues.

```
(def a (atom 0))

(specification "Some Thing"
  (with-timeline
    (provided "things happen in order"
              (js/setTimeout f tm) =2x=> (async tm (f))

              (js/setTimeout
                (fn []
                  (reset! a 1)
                  (js/setTimeout
                    (fn [] (reset! a 2)) 200)) 100)

              (tick 100)
              (is (= 1 @a))

              (tick 100)
              (is (= 1 @a))

              (tick 100)
              (is (= 2 @a))))
```

In the above scripted test the `provided` (when-mocking with a label) is used to mock out `js/setTimeout`. By
wrapping that provided in a `with-timeline` we gain the ability to use the `async` and `tick` macros (which must be
pulled in as macros in the namespace). The former can be used on the RHS of a mock to indicate that the actual
behavior should happen some number of milliseconds in the *simulated* future.

So, this test says that when `setTimeout` is called we should simulate waiting however long that
call requested, then we should run the captured function. Note that the `async` macro doesn't take a symbol to
run, it instead wants you to supply a full form to run (so you can add in arguments, etc).

Next this test does a nested `setTimeout`! This is perfectly fine. Calling the `tick` function advances the
simulated clock. So, you can see we can watch the atom change over \"time\"!

Note that you can schedule multiple things, and still return a value from the mock!

```
(with-timeline
  (when-mocking
     (f a) => (do (async 200 (g)) (async 300 (h)) true)))
```

the above indicates that when `f` is called it will schedule `(g)` to run 200ms from \"now\" and `(h)` to run
300ms from \"now\". Then `f` will return `true`.

## Clojure/cljs test

You can use regular clojure test macros (is, are, etc). This is all clojure/cljs test underneath. `specification` is basically `deftest`.

## Other things of interest

Untangled spec also has:

- `component`: Identical to behavior, but is useful for making specs more readable (creates a sub-section of outline for a sub-area)
- `provided`: Similar to `when-mocking`, but requires a string, which is added as a subsection of the outline. The idea
with this is that your mocking is stating an assumption about some way other parts of the system are behaving for that test.

## License

MIT License
Copyright © 2015 NAVIS

