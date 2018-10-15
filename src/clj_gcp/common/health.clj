(ns clj-gcp.common.health
  (:require [clj-gcp.common.specs :as cs]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [ring.util.http-response :as resp]))

(s/def ::name ::cs/non-blank-string)
(s/def ::healthy? boolean?)
(s/def ::dependencies (s/coll-of ::healthcheck))
(s/def ::healthcheck
  (s/keys :req-un [::name ::healthy?] :opt-un [::dependencies]))

(s/def ::healthcheck-fns
  (s/coll-of fn?))
(s/def ::app
  (s/keys :req-un [::name]))

(defn perform-healthcheck
  [{:keys [healthcheck-fns app]}]
  (let [deps (pmap #(%) healthcheck-fns)
        health {:name (:name app),
                :app app,
                :healthy? (every? :healthy? deps),
                :dependencies deps}]
    (when-not (s/valid? ::healthcheck health)
      (log/error "Did not conform to spec" health))
    health))

(defn ->healthcheck-handler
  [opts]
  (fn [_req]
    (let [result (perform-healthcheck opts)]
      (if (:healthy? result)
        (resp/ok result)
        (resp/service-unavailable result)))))

(defmethod ig/pre-init-spec ::healthcheck-handler [_]
  (s/keys :req-un [::healthcheck-fns ::app]))
(defmethod ig/init-key ::healthcheck-handler
  [_ opts]
  (->healthcheck-handler opts))

(defmethod ig/init-key ::alive? [_ _] (atom true))
