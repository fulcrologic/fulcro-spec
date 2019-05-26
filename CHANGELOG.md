3.1.0
-----
- Fixed terminal reporter. It was failing to show diffs on unlabeled assertions.
- BREAKING CHANGE. Changed (broken) `=throws=>`, and simplified the syntax. 
Use either a simple classname, or a regex:

```
(f) =throws=> ArithmeticException
(g) =throws=> #"message"
```

3.0.1
-----
- Added arrow declarations in core
- Fixed a deps error

3.0.0
-----
- BREAKING: Removed runners
- See docs for new way to use.

2.1.3
-----
- Made fulcro spec events not propagate to clojure test reporter, so
  test runners don't see the events.

2.1.2
-----
- Updated dependencies
- Ported to defsc (finally)
- Fixed a few glicthes that seemed to be affecting rendering

2.1.1
-----
- Added spec-checking `provided!` and `when-mocking!`

2.1.0
-----
- Upgrade to Clojure 1.9, Clojurescript 1.10
- Removed use of clojure future spec
- One bug fix to work with new DOM

2.0.4
-----
- Fixed error on startup related to selectors
- Change assertions internals to not side-effect in a setup macro.

2.0.3
-----
- Fixed bug with server-side test rendering. A multimethod was expected by the back-end but we were passing a fn.

2.0.2
-----
- Fixed an expression that refused to compile in shadow-cljs

2.0.1
-----
- Removed dependency on timbre

2.0.0
-----
- Upgraded to use Fulcro 2.x.

1.0.0
-----
- Fixed clj test running to include stack trace on unexpected throws

1.0.0-beta9
-----------
- bugfix: client renderer tried to connect to server test websocket

1.0.0-beta8
-----------
- Fixed bug in server renderer due to networking change (beta7 is broken for server-in-browser)

1.0.0-beta1
-----------
- Renamed to fulcro-spec
- Ported to clojure.spec.alpha
- Adding ability to render clojure tests in the browser!
- WIP: when-mocking & provided will use clojure.spec.alpha to verify the :args passed to mocks, the :ret value you specify, and their relationship if a :fn spec if they exist.
- Adding selectors to the specification macro
    - They emit meta data on the deftest var so they are compatible with anything,
      but also wrap the body so that fulcro-spec can properly run just the selected tests.
- Fixed minor bug in renderer that could cause failure to start
- Backported to Clojure 1.8. Should now work in 1.8 and 1.9

0.4.0
-----
Lots of bug/issue fixes:
- gh-6 -> fixed by work in gh-17
- gh-8 -> using edn/read-string on "[" m "]" or falling back to just the message itself
- gh-10 -> using js/alert for now
- gh-11 -> by adding fix-str which handles nil and empty string as special cases
- gh-13 -> adding test selector support in specification macro
- gh-16 -> by wrapping the generated mock code in do-report's
- gh-17 -> by using (& tweaking) diff/patch in the edn renderer
- gh-18 -> can now capture varargs in a stub!
- gh-21 -> added history to stubs for use in debugging when the stub is not called the required number of times
- gh-21 -> improved messaging for when a stub is called with invalid arguments (ie: count mismatch or failing literal)
- gh-21 -> reporting failing arguments when a stub is called too many times
- gh-21 -> when validating stubs, failures will contain in their ex-data, the whole script, and each step will now also have history, (for cases when you are mocking a function with multiple steps, eg: =1x=> ...  =2x=>)
- gh-23 -> =throws=> now additionally supports a symbol, OR a map with optional keys: :ex-type :regex :fn
- gh-25 -> added pending & passing filters
- gh-28 -> using clojure.spec to parse provided & when-mocking
- gh-32 -> fixing broken conform

Assorted fixes/improvements:
- added exception handling to diff rendering
- fixing stub arg validation to allow for no literals
- fixing weird bug with diff patch where different size vectors crash the patch fn

0.3.9
-----
- Fixed bug with diff algorithm, see github issue #3

0.3.8
-----
- Fixed bug with diff algorithm, see github issue #2

0.3.7
-----
- Updated to work with React 15 and Om 36

0.3.6 - April 22, 2016
-----
- Added fulcro-spec.reporters.terminal/merge-cfg!
    - No arguments will print the valid keys, and if you pass it a map it will
      verify that you are only modifying existing key-value pairs.
- Adding gensym to the symbol specification generates for deftest.
    - Conflicts with specification names & any other vars are now impossible
- Can now configure pprint *print-level* & *print-depth* using fulcro-spec.reporters.terminal/merge-cfg!

0.1.1
-----
- Added support for new macros:
    - `when-mocking`: Just like `provided`, but without output (no string parameter)
    - `component`: Alias for `behavior`. Makes specs read better.
- `behavior` macro now supports :manual-test as a body, which will indicate that the test requires a human to do the steps.

