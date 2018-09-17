(ns clj-gcp.integration.pub-sub.core-test
  (:require [cheshire.core :as json]
            [clj-gcp.integration.pub-sub.utils :as mqu]
            [clj-gcp.pub-sub.admin :as sut-admin]
            [clj-gcp.pub-sub.core :as sut]
            [clj-gcp.test-utils :as tu]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [iapetos.core :as prometheus])
  (:import java.util.UUID))

(def gcp-project-id (System/getenv "GCP_PROJECT_ID"))

(defn uuid [] (str (UUID/randomUUID)))

(defn with-random-topic+subscription
  [project-id f]
  (let [topic-id (str "DELETE-ME.topic." (uuid))
        sub-id   (str "DELETE-ME.subscription." (uuid))]
    (sut-admin/create-topic project-id topic-id)
    (sut-admin/create-subscription project-id topic-id sub-id 0)
    (try (is (sut-admin/get-subscription project-id sub-id))
         (f topic-id sub-id)
         (finally (sut-admin/delete-subscription project-id  sub-id)
                  (sut-admin/delete-topic project-id topic-id)))))

(defn see-req!
  "Small utility to hook in the debugger."
  [seen-reqs req]
  (swap! seen-reqs conj req))

(defn with-subscriber
  [seen-reqs f]
  (with-random-topic+subscription gcp-project-id
    (fn [topic-id sub-id]
      (let [opts            {:project-id      gcp-project-id
                             :topic-id        topic-id
                             :subscription-id sub-id
                             :handler         (fn [msgs]
                                                (doseq [msg msgs]
                                                  (see-req! seen-reqs msg))
                                                (map #(assoc % :ok? true) msgs))
                             :metrics-registry
                             (-> (prometheus/collector-registry)
                                 (prometheus/register
                                  (prometheus/counter
                                   :clj-gcp.pub-sub.core/message-count
                                   {:description "life-cycle of pub-sub msgs",
                                    :labels      [:state]})))}
            _               (tu/is-valid ::sut/subscriber.opts opts)
            stop-subscriber (sut/start-subscriber opts)]
        (f topic-id sub-id stop-subscriber)))))

(deftest  ^:integration subscriber-test
  (let [seen-msgs (atom [])]
    (with-subscriber seen-msgs
      (fn [topic-id subscription-id stop-subscriber]
        (testing "getting messages"
          (let [msg1 (json/generate-string {:a "A" :b "B"})
                msg2 (json/generate-string {:a "A2" :b "B2"})]
            (mqu/pubsub-publish msg1 {"eventType" "SOME_TYPE"} gcp-project-id topic-id)
            (mqu/pubsub-publish msg2 {"eventType" "SOME_OTHER_TYPE"} gcp-project-id topic-id)
            (tu/is-eventually (= 2 (count @seen-msgs))
                              :timeout 20000)
            ;; no guarantees on ordering with PubSub (so using a set to check messages arrived)
            (let [actual          @seen-msgs
                  without-ack-id  (map #(dissoc % :pubsub/ack-id) actual)
                  wo-ack-set      (set without-ack-id)]

              ;; ack-ids are generate by GCP so just check they are added to the incoming message
              (is (every? #(not (string/blank? (:pubsub/ack-id %))) actual))

              (is (wo-ack-set
                   {:a                 "A"
                    :b                 "B"
                    :pubsub/attributes {:eventType "SOME_TYPE"}}))
              (is (wo-ack-set
                   {:a                 "A2"
                    :b                 "B2"
                    :pubsub/attributes {:eventType "SOME_OTHER_TYPE"}})))

            (stop-subscriber)))))))

