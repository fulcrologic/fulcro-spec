1.0.0
-----
- Ported to clojure.spec.alpha
- Adding ability to render clojure tests in the browser!
- WIP: when-mocking & provided will use clojure.spec.alpha to verify the :args passed to mocks, the :ret value you specify, and their relationship if a :fn spec if they exist.
- Adding selectors to the specification macro that work with untangled-spec, test-refresh, doo, etc...
    - They emit meta data on the deftest var so they are compatible with anything,
      but also wrap the body so that untangled-spec can properly run just the selected tests.

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
- Added untangled-spec.reporters.terminal/merge-cfg!
    - No arguments will print the valid keys, and if you pass it a map it will
      verify that you are only modifying existing key-value pairs.
- Adding gensym to the symbol specification generates for deftest.
    - Conflicts with specification names & any other vars are now impossible
- Can now configure pprint *print-level* & *print-depth* using untangled-spec.reporters.terminal/merge-cfg!

0.1.1
-----
- Added support for new macros:
    - `when-mocking`: Just like `provided`, but without output (no string parameter)
    - `component`: Alias for `behavior`. Makes specs read better.
- `behavior` macro now supports :manual-test as a body, which will indicate that the test requires a human to do the steps.

