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

