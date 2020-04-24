(ns clj-gcp.common.health-test
  (:require [clj-gcp.common.health :as health]
            [clj-gcp.test-utils :as tu]
            [clojure.test :refer :all])
  (:import java.nio.channels.Channels))

(deftest perform-healthcheck-ok
  (let [fn-a        (fn [] {:name "A" :healthy? true})
        healthcheck (health/perform-healthcheck {:healthcheck-fns [fn-a]
                                                 :app {:name "test"}})]
    (tu/is-valid ::health/healthcheck healthcheck)
    (is (:healthy? healthcheck))))

(deftest perform-healthcheck-nok
  (let [fn-a        (fn [] {:name "A" :healthy? true})
        fn-b        (fn [] {:name "B" :healthy? false})
        healthcheck (health/perform-healthcheck {:healthcheck-fns [fn-a fn-b]
                                                 :app {:name "test"}})]
    (tu/is-valid ::health/healthcheck healthcheck)
    (is (not (:healthy? healthcheck)))))
