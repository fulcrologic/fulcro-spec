---
name: clojure-repl
description: Automatically manage and use nREPL servers to run/evaluate Clojure code and tests.
---

# Clojure REPL Management

Automatically handle all Clojure REPL operations without requiring manual commands from the user.

## Core Capabilities

Use bash to run this in the background:

**How to start nREPL:**
```bash
clojure -A:test -M:test-nrepl
```

The nREPL server will:
- Start on an available port (written to `.nrepl-port`)

If you detect that the repl is malfunctioning, kill the background process and start a new one.

### 2. Code Evaluation

There is a CLI tool called `clj-nrepl-eval`. You can use that to send clojure expressions to the running REPL:

```bash
clj-nrepl-eval --timeout 60000 -p $(cat .nrepl-port)  <<'EOF'
(def x 10)
(+ x 20)
EOF
```

If you want to attempt to reset the session, you can try:

```bash
clj-nrepl-eval -p $(cat .nrepl-port) --reset-session 
```
