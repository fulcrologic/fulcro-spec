tests:
	pnpm install --frozen-lockfile
	pnpm exec shadow-cljs compile ci-tests
	pnpm exec karma start --single-run
	clojure -M:test:clj-tests

.PHONY: tests
