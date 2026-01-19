(ns fulcro-spec.signature
  "Signatures are a CLJ-only feature. This is here for compilation of CLJC without issues.")

(defn already-checked? [& _] true)
(defn signature [& _] nil)
(defn self-signature [& _] nil)
(defn auto-skip-enabled? [& _] false)
(defn sigcache-enabled? [& _] false)
(defn leaf? [& _] false)
