tests:
	npm install
	lein doo chrome automated-tests once

test-server:
	lein test-refresh

test-client:
	rlwrap lein figwheel

help:
	@ make -rpn | sed -n -e '/^$$/ { n ; /^[^ ]*:/p; }' | sort | egrep --color '^[^ ]*:'

.PHONY: test-server test-client tests help