# smooth-test

A Clojure library designed to ... well, that part is up to you.

DEVELOPMENT NOTES:

To run tests:

     rlwrap lein figwheel dev test

- Add specs to test folder. Use cljs.test for now.
- Require the spec in dev/cljs/user.cljs
- Add spec namespace to run-all-tests call to run-tests
- focus auto-run via the user.cljs on-load function


## Usage

    (specification "The Thing"
      (provided "successfully fetching remote data"
         ;; (capture x) grabs the actual argument passed
         ;; special value 'anything'
         ;; literal values must match exactly
         ;; async keyword meaning: Schedule an async call that does X after so many ms
         (get "/a/b" (capture good) anything) => (async 200 (good ..data.. arg2)) ; calls (good arg1 arg2) after 200ms (simulated time)
         ;; NOTE: these are times relative to the call itself. (thing) happens 100ms after setTimeout
         (.setTimeout (capture thing) 100) => (async 100 (thing))
         ;; IF (good) triggers the setTimeout, then (thing) happens in 300ms sim time. If the same
         ;; function triggers both the setTimeout and get, then (thing) happens in 100ms. In
         ;; all cases, the test itself simulates the time as quickly as possible, so the overall
         ;; test probably takes a few microseconds.

         (behavior "stores the data and triggers a thing"
             ;; sim clock starts at 0
             (read-data) => anything ; triggers (get ...)
             ;; (good) placed in simulation queue @ clock time 200
             (get-fetched-data) => :empty
             (get-state-change-caused-by-thing) => :none
             clock-ticks => 201
             ; (good) is called, which calls setTimeout. (thing) placed @ clock time 300
             (get-fetched-data) => ..data..
             (get-state-change-caused-by-thing) => :none
             clock-ticks => 100 ; clock now at 301...(thing) triggered
             (get-state-change-caused-by-thing) => :done
             )
             ;; test fails if setTimeout or get were not called
      ))


## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
