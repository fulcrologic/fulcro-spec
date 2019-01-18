tests:
	clojure -A:clj-tests
	npm install
	npx shadow-cljs compile ci-tests
	npx karma start --single-run

.PHONY: tests
