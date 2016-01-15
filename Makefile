tests:
	npm install
	lein doo chrome automated-tests once

test-server:
	lein test-refresh

test-client:
	rlwrap lein figwheel
