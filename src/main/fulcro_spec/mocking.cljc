(ns fulcro-spec.mocking
  (:require
    [fulcro-spec.stub :as stub]))

(defn original-fn
  "For use in the RHS of a when-mocking (or similar).
   The zero arity returns the original function, thereby avoiding a stackoverflow error with the mocked implementation.
   The many arity is equivalent to calling the original function with all of the passed in arguments.
   Example:
   ```
   (defn f [a] [:real a])

   (when-mocking
     (f a) =1x=> (original-fn)
     (f a) =1x=> (original-fn 5)
     (assertions
       (f 0) => f
       (f 1) => [:real 5]))
   ```"
  ([] (stub/original-fn))
  ([& args] (apply stub/original-fn args)))

(defn real-return
  "For use in the RHS of a when-mocking (or similar).
   Will return what the original call to the stubbed function would have returned if passed the specified arguments in the LHS.
   Example:
   ```
   (defn f [a] :real)

   (when-mocking
     (f a) =1x=> (real-return)
     (assertions
       (f 1) => :real))
   ```"
  []
  (stub/real-return))

(defn returns-of
  "Returns all the return values of the mocked function `f`.
   Will return nil if it could not get the return values, either you passed it the wrong function or you are not inside a `when-mocking` or similar.
   Example:
   ```
   (defn f [a] :real)

   (when-mocking
     (f a) => (real-return)
     (do (f 1)
       (assertions
         (returns-of f) => [:real/f])))
   ```"
  [f]
  (stub/returns-of f))

(defn return-of
  "Returns the return value that the mocked function `f` returned at the (0 based) `index`'ed call.
   Returns nil if the function was not mocked, or the index was out of bounds.
   Example:
   ```
   (defn f [a] :real)

   (when-mocking
     (f a) => (real-return)
     (do (f 1)
       (assertions
         (return-of f 0) => :real/f)))
   ```"
  [f index]
  (stub/return-of f index))

(defn calls-of
  "Returns all the arguments the mocked function `f` received, keyed by the symbols specified in the mock arrow.
   Will return nil if it could not get the arguments, meaning either you passed it the wrong function or you are not inside a `when-mocking` or similar.
   Example:
   ```
   (defn f [a b] :real)

   (when-mocking
     (f a1 b2) => :mocked/f
     (do (f 1 2)
       (assertions
         (calls-of f)
         => [{'a1 1, 'b2 2}])))
   ```"
  [f]
  (stub/calls-of f))

(defn call-of
  "Returns the arguments the mocked function `f` received at the (0 based) `index`'ed call, keyed by the symbols specified in the mock arrow.
   Returns nil if the function was not mocked, or the index was out of bounds.
   Example:
   ```
   (defn f [a] :real)

   (when-mocking
     (f a1) => (real-return)
     (do (f 1)
       (assertions
         (call-of f 0)
         => {'a1 1})))
   ```"
  [f index]
  (stub/call-of f index))

(defn spied-value
  "Returns the argument `sym` that the mocked function `f` received at the (0 based) `index`'ed call.
   Returns nil if the function was not mocked, the index was out of bounds, or the symbol was not found.
   Example:
   ```
   (defn f [a] :real)

   (when-mocking
     (f a1) => (real-return)
     (do (f 1)
       (assertions
         (spied-value f 0 'a1)
         => 1)))
   ```"
  [f index sym]
  (stub/spied-value f index sym))
