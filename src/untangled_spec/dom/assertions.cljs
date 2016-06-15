(ns untangled-spec.dom.assertions
  (:require [goog.dom :as gd]
            [cljs.test :refer-macros [is]]
            [clojure.string :as str]
            [untangled-spec.dom.util :as util]
            [cljs.test :as t]))

(defn text-matches
  "A test assertion (like is) that checks that the text on a DOM node matches the given string
  (which is treated as a regex pattern)."
  [string dom-node]
  (is (not (nil? string)) "STRING IS NIL")
  (is (not (nil? dom-node)) (str "DOM NODE IS NIL for regex " string))
  (if (and string dom-node)
    (let [regex (js/RegExp. string)
          text (gd/getTextContent dom-node)
          ]
      (is (.test regex text) (str text " matches " string)))))

(defn has-visible-text
  "A test assertion that uses find-element to process search-kind and search-param on the given dom, then
  asserts (cljs.test/is) that the given element has the given text."
  [text-or-regex search-kind search-param dom]
  (if-let [ele (util/find-element search-kind search-param dom)]
    (if-not (util/node-contains-text? text-or-regex ele)
      (t/do-report {:type :fail :actual (gd/getTextContent ele) :expected text-or-regex})
      (t/do-report {:type :pass})
      )
    (t/do-report {:type     :error :message (str "Could not find DOM element")
                  :expected (str "DOM element specified by " search-kind " " search-param) :actual "Nil"})
    )
  )

(defn has-element
  "A test assertion that uses find-element to process search-kind and search-param on the given dom, then
  asserts that the returned element is not nil."
  [search-kind search-param dom]
  (if (util/find-element search-kind search-param dom)
    (t/do-report {:type :pass})
    (t/do-report {:type     :error :message (str "Could not find DOM element")
                  :expected (str "DOM element specified by " search-kind " " search-param) :actual "Nil"})))

(defn has-attribute [attr search-kind search-param dom]
  "A test assertion that uses find-element to process search-kind and search-param on the given dom, then
  asserts that the given element contains the attribute passed in 'attr' as a keyword. This assertion
  DOES NOT test that an attribute has a given value, just that the attribute exists (e.g. 'checked' on an
  input checkbox). To assert that an attribute has a given value, use has-attribute-value."
  (if-let [ele (util/find-element search-kind search-param dom)]
    (if (util/get-attribute ele attr)
      (t/do-report {:type :pass})
      (t/do-report {:type   :fail
                    :actual "Attribute not found" :expected (name attr)}))
    (t/do-report {:type     :error :message (str "Could not find DOM element")
                  :expected (str "DOM element specified by " search-kind " " search-param) :actual "Nil"}))
  )

(defn has-attribute-value [attr desired-value search-kind search-param dom]
  "A test assertion that uses find-element to process search-kind and search-param on the given dom, then
  asserts that the given element contains the attribute 'attr=value', where 'attr' is a keyword and
  'value' is a string. DO NOT use this function to test for attributes without an assigned value
  (e.g. 'checked' on an input checkbox), use has-attribute instead."
  (if-let [ele (util/find-element search-kind search-param dom)]
    (if-let [actual-value (util/get-attribute ele attr)]
      (if (= actual-value desired-value)
        (t/do-report {:type :pass})
        (t/do-report {:type   :fail
                      :actual (str (name attr) "=" actual-value) :expected (str (name attr) "=" desired-value)}))
      (t/do-report {:type     :error :message (str "Could not find attribute")
                    :expected (str (name attr) "=" (if (nil? desired-value) "nil" desired-value))
                    :actual   (str "Attribute '" (name attr) "' not found")}))
    (t/do-report {:type     :error :message (str "Could not find DOM element")
                  :expected (str "DOM element specified by " search-kind " " search-param) :actual "Nil"})))

(defn has-class [class-name search-kind search-param dom]
  "A test assertion that uses find-element to process search-kind and search-param on the given dom, then
  asserts that the given element contains a class named 'class-name', where 'class-name is a string."
  (if-let [ele (util/find-element search-kind search-param dom)]
    (if (.contains gd/classlist ele class-name)
      (t/do-report {:type :pass})
      (t/do-report {:type   :fail
                    :actual (str "Class '" class-name "' not found in DOM element specified by " search-kind " " search-param) :expected (str class-name " found in class attribute.")}))
    (t/do-report {:type     :error :message (str "Could not find DOM element")
                  :expected (str "DOM element specified by " search-kind " " search-param) :actual "Nil"})))

(defn has-selected-option [search-type search-value dom-with-select selected-value]
  (if-let [ele (util/find-element search-type search-value dom-with-select)]
    (if (= "select" (util/tag-name ele))
      (let [selection (.-value ele)]
        (if (= selection selected-value)
          (t/do-report {:type :pass})
          (t/do-report {:type :fail :actual selection :expected selected-value})
          )
        )
      (t/do-report {:type     :error :message (str "Element at " search-type " " search-value " IS NOT a SELECT")
                    :expected "select" :actual (util/tag-name ele)
                    })
      )
    (t/do-report {:type     :error :message (str "Could not find a select element at " search-type " " search-value)
                  :expected "DOM element" :actual "Nil"
                  })
    )
  )
