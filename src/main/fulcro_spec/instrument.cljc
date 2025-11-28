(ns fulcro-spec.instrument
  "Cross-platform instrumentation functions for mock validation.
   These wrap stub functions to validate args/return against Malli schemas or Clojure Specs."
  (:require
    [clojure.string :as str]
    #?(:clj  [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])
    [malli.core :as mc]
    [com.fulcrologic.guardrails.malli.registry :as gr.reg]))

(defn- malli-function-info
  "Extract function schema information for Malli :=> schemas."
  [schema options]
  (when (= (mc/type schema options) :=>)
    (let [[input output guard] (mc/-children schema)
          {:keys [min max]} (mc/-regex-min-max input false)]
      (cond-> {:min    min
               :arity  (if (= min max) min :varargs)
               :input  input
               :output output}
        guard (assoc :guard guard)
        max (assoc :max max)))))

(defn malli-instrument
  "Wraps a stub function with Malli validation. Reports errors via the report callback."
  [f schema report]
  (let [opts {:registry gr.reg/registry}]
    (case (mc/type schema opts)
      :=> (let [{:keys [min max input output guard]} (malli-function-info schema opts)
                [validate-input validate-output validate-guard]
                (mc/-vmap
                  (fn [s] (fn [v] (mc/validate s v opts)))
                  [input output (or guard :any)])]
            (fn [& args]
              (let [args (vec args), arity (count args)]
                (try
                  (when-not (<= min arity (or max 1000))
                    (report ::invalid-arity {:arity arity :arities #{{:min min :max max}} :args args}))
                  (when-not (validate-input args)
                    (report ::invalid-input {:input input :args args}))
                  (catch #?(:clj Exception :cljs :default) e
                    (report ::malli-error {:args args :original-error (ex-message e)})))
                (let [value (apply f args)]
                  (try
                    (when (and output (not (validate-output value)))
                      (report ::invalid-output {:output output :value value :args args}))
                    (when (and guard (not (validate-guard [args value])))
                      (report ::invalid-guard {:guard guard :value value :args args}))
                    (catch #?(:clj Exception :cljs :default) e
                      (report ::malli-error {:value value :args args :original-error (ex-message e)})))
                  value))))
      :function (let [arity->info  (->> (mc/children schema opts)
                                     (map (fn [s]
                                            (assoc (malli-function-info s opts)
                                              :f (malli-instrument f s report))))
                                     (mc/-group-by-arity!))
                      arities      (-> arity->info keys set)
                      varargs-info (arity->info :varargs)]
                  (if (= 1 (count arities))
                    (-> arity->info first val :f)
                    (fn [& args]
                      (let [arity (count args)
                            {:keys [input] :as info} (arity->info arity)]
                        (cond
                          info (apply (:f info) args)
                          varargs-info (if (< arity (:min varargs-info))
                                         (report ::invalid-arity {:arity arity :arities arities :args args})
                                         (apply (:f varargs-info) args))
                          :else (report ::invalid-arity {:arity arity :arities arities :args args})))))))))

(defn spec-instrument
  "Wraps a stub function with Clojure Spec validation. Reports errors via the report callback."
  [f fspec-sym report]
  (if-let [fspec (s/get-spec fspec-sym)]
    (let [args-spec (:args fspec)
          ret-spec  (:ret fspec)
          fn-spec   (:fn fspec)]
      (fn [& args]
        (try
          (when (and args-spec (not (s/valid? args-spec args)))
            (report ::invalid-spec-input
              {:spec args-spec :args args :problems (s/explain-data args-spec args)}))
          (catch #?(:clj Exception :cljs :default) e
            (let [msg (or (ex-message e) "")]
              (when (or (str/includes? msg "Unable to resolve spec")
                      (str/includes? msg "no method"))
                (report ::spec-resolution-error {:fspec-sym fspec-sym :args args :original-error msg})))))
        (let [value (apply f args)]
          (try
            (when (and ret-spec (not (s/valid? ret-spec value)))
              (report ::invalid-spec-output
                {:spec ret-spec :value value :args args :problems (s/explain-data ret-spec value)}))
            (when fn-spec
              (let [fn-check-input {:args args :ret value}]
                (when-not (s/valid? fn-spec fn-check-input)
                  (report ::invalid-spec-fn
                    {:spec fn-spec :value value :args args :problems (s/explain-data fn-spec fn-check-input)}))))
            (catch #?(:clj Exception :cljs :default) e
              (let [msg (or (ex-message e) "")]
                (when (or (str/includes? msg "Unable to resolve spec")
                        (str/includes? msg "no method"))
                  (report ::spec-resolution-error {:fspec-sym fspec-sym :value value :args args :original-error msg})))))
          value)))
    ;; No fspec found, return function unchanged
    f))
