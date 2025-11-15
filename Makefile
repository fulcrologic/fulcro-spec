tests:
	npm install
	npx shadow-cljs compile ci-tests
	npx karma start --single-run
	clojure -M:test:clj-tests

.PHONY: tests
