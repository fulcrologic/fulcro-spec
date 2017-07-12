(ns fulcro-spec.dom.edn-renderer
  (:require
    [cljs.pprint :refer [pprint]]
    [fulcro-spec.diff :as diff]
    [om.dom :as dom])
  (:import
    (goog.string StringBuffer)))

(defonce ^:dynamic *key-counter* nil)

(defn get-key []
  (swap! *key-counter* inc)
  (str "k-" @*key-counter*))

(declare html)

(defn literal? [x]
  (and (not (seq? x))
    (not (coll? x))))

(defn separator* [s]
  (dom/div #js {:className "separator"
                :key (get-key)}
    s))

(defn clearfix-separator* [s]
  (dom/span #js {:key (get-key)}
    (separator* s)
    (dom/span #js {:className "clearfix"})))

(defn separate-fn [coll]
  (if (not (every? literal? coll)) clearfix-separator* separator*))

(defn interpose-separator [rct-coll s sep-fn]
  (->> (rest rct-coll)
    (interleave (repeatedly #(sep-fn s)))
    (cons (first rct-coll))
    to-array))

(defn pprint-str [obj]
  (let [sb (StringBuffer.)]
    (pprint obj (StringBufferWriter. sb))
    (str sb)))

(defn literal [class x]
  (dom/span #js {:className class :key (get-key)}
    (pprint-str x)))

(defn join-html [separator coll]
  (interpose-separator (mapv html coll)
    separator
    (separate-fn coll)))

(defn html-keyval [[k v]]
  (dom/span #js {:className "keyval"
                 :key (prn-str k)}
    (html k)
    (html v)))

(defn html-keyvals [coll]
  (interpose-separator (mapv html-keyval coll)
    " "
    (separate-fn (vals coll))))

(defn open-close [class-str opener closer rct-coll]
  (dom/span #js {:className class-str :key (str (hash rct-coll))}
    (dom/span #js {:className "opener"   :key 1} opener)
    (dom/span #js {:className "contents" :key 2} rct-coll)
    (dom/span #js {:className "closer"   :key 3} closer)))

(defn html-collection [class opener closer coll]
  (open-close (str "collection " class ) opener closer (join-html " " coll)))

(defn html-map [coll]
  (open-close "collection map" "{" "}" (html-keyvals coll)))

(defn html-string [s]
  (open-close "string" "\"" "\"" s))

(defn html [x]
  (cond
    (number? x)  (literal "number" x)
    (keyword? x) (literal "keyword" x)
    (symbol? x)  (literal "symbol" x)
    (string? x)  (html-string x)
    (map? x)     (html-map x)
    (set? x)     (html-collection "set"    "#{" "}" x)
    (vector? x)  (html-collection "vector" "[" "]" x)
    (seq? x)     (html-collection "seq"    "(" ")" x)
    :else        (literal "literal" x)))

(defn html-edn [e & [diff]]
  (binding [*key-counter* (atom 0)]
    (dom/div #js {:className "rendered-edn com-rigsomelight-devcards-typog"}
      (try
        (html (cond-> e diff (diff/patch diff)))
        (catch js/Object e (html "DIFF CRASHED ON OUTPUT"))))))
