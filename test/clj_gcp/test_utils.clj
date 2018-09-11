(ns clj-gcp.test-utils
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            clojure.test
            [clojure.tools.logging :as log]
            [expound.alpha :as expound]
            [integrant.core :as ig]))

(defmacro is-valid
  [spec value]
  `(clojure.test/is (s/valid? ~spec ~value)
                    (str (expound.alpha/expound-str ~spec ~value))))

(defn eventually?-fn [f & [{:keys [timeout] :or {timeout 10000}}]]
  (let [fut (future
              (loop [result (f)]
                (if result
                  result
                  (do (Thread/sleep 100)
                      (recur (f))))))]
    (try
      (deref fut timeout false)
      (finally
        (future-cancel fut)))))

(defmacro eventually [body & args]
  `(eventually?-fn (fn [] (do ~body)) ~@args))

(defmacro is-eventually [body & args]
  `(do
     ;; wait first
     (eventually?-fn (fn [] (do ~body)) ~@args)
     ;; then assert
     (clojure.test/is ~body)))
