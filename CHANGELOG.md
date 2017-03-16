0.4.0
-----
    broken-conform -> patching issue #31
    made behavior name allow expr
    fixed rendering typo
    fixed spec for behavior
    fixed specification and behavior to accept symbols for strings
    added exception handling to diff rendering
    gh-25 -> fixing terminal reporting coloring for failing tests
    fixed gh-16 -> by wrapping the generated mock code in do-report's
    - also refactored do-report code to a with-reporting macro
    gh-28 -> using clojure.spec to parse provided & when-mocking
    fixed gh-23 -> done! =throws=> now additionally supports a symbol
    OR a map with optional keys: :ex-type :regex :fn
    gh-21 -> when validating stubs, failures will contain in their ex-data
        the whole script, and each step will now also have history
        (for cases when you are mocking a function with multiple steps, eg: =1x=> ...  =2x=>)
    - fixing history test breaking reporting
    - fixing stub arg validation to allow for no literals
    gh-28 -> refactoring filters for readability
    gh-28 -> refactoring by consolidating reporter code into base_reporter
    fixed gh-10 -> just using js/alert for now
    gh-10: WIP but close! file reload seems to cause it to close the warning
    removing non-existant test form tests_to_run
    reverting manual testing back to just :manual-test
    upgrading few obvious deps & removing unused assertions helper
    fixing weird bug with diff patch where different size vectors crash the patch fn
    fixing gh-11 by adding fix-str which handles nil and empty string as special cases
    fixed gh-6 -> already fixed by work in gh-17
    fixed gh-25 -> optimized by changing status to be a counter of all the status types
    - This way the checking of 'type' is more efficient algorithmically as
      we are far more likely to have the necessary information in the current node
    gh-25: auto gensym ing suite macro output
    gh-25: improved/fixed manual & pending filters
    NOTE: should probably tweak the reporting accumulation algorithm, see issue page
    gh-25: refactoring unique keyfn to a helper fn
    gh-25: added pending & passing filters
    - refactored filter related ui code
    gh-25 -> fixing dom indentation
    fixed gh-8 w/ edn/read-string on "[" m "]" or falling back to just the message itself
    gh-13 fixed by adding test selector support in specification macro
    wrapping assertions macro with clojure.spec
    gh-21: added history to stubs for use in debugging when the stub is not
    called the required number of times
    fixed gh-18: can now capture varargs in a stub!
    gh-21: reporting failing arguments when a stub is called too many times
    gh-21: improved messaging for when a stub is called with invalid
    arguments (ie: count mismatch or failing literal)
    fixed #17 by using (& tweaking) patch in the edn renderer
    Lots of documentation

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

