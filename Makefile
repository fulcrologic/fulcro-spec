tests:
	npm install
	lein test-cljs
	lein test

deploy:
	lein with-profile with-cljs deploy clojars

help:
	@ make -rpn | sed -n -e '/^$$/ { n ; /^[^ ]*:/p; }' | sort | egrep --color '^[^ ]*:'

.PHONY: tests help
