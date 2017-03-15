tests:
	npm install
	lein test-cljs
	lein test-clj

travis-tests:
	npm install
	lein with-profile test doo firefox automated-tests once
	lein test-clj

help:
	@ make -rpn | sed -n -e '/^$$/ { n ; /^[^ ]*:/p; }' | sort | egrep --color '^[^ ]*:'

.PHONY: tests help
