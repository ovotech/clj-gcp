(ns clj-gcp.pub-sub.core
  (:require [cheshire.core :as json]
            [clj-gcp.pub-sub.admin :as pub-sub-admin]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [iapetos.core :as prometheus]
            [integrant.core :as ig]
            [clj-gcp.common.specs :as cs])
  (:import com.google.api.gax.rpc.DeadlineExceededException
           [com.google.cloud.pubsub.v1.stub GrpcSubscriberStub SubscriberStub SubscriberStubSettings]
           [com.google.pubsub.v1 AcknowledgeRequest ProjectSubscriptionName PubsubMessage PullRequest ReceivedMessage]
           java.util.concurrent.TimeUnit))

(s/def ::project-id
  ::cs/non-blank-string)
(s/def ::subscription-id
  ::cs/non-blank-string)
(s/def ::max-messages
  pos-int?)
(s/def ::handler
  fn?)
(s/def ::subscriber.opts
  (s/keys :req-un [::handler
                   ::project-id
                   ::subscription-id
                   ::metrics-registry]
          :opt-un [::pull-max-messages]))
(s/def ::subscriber.healthcheck.opts
  (s/keys :req-un [::project-id
                   ::subscription-id]))

(defn- pull-msgs
  [^SubscriberStub subscriber ^String subscription-name max-messages]
  (let [pull-req (-> (PullRequest/newBuilder)
                     (.setMaxMessages max-messages)
                     (.setReturnImmediately false)
                     (.setSubscription subscription-name)
                     .build)
        pull-resp (-> subscriber
                      .pullCallable
                      (.call pull-req))]
    (.getReceivedMessagesList pull-resp)))

(defn- ack-msgs
  [^SubscriberStub subscriber ^String subscription-name msgs]
  (let [ack-ids (map :pubsub/ack-id msgs)
        ack-req (-> (AcknowledgeRequest/newBuilder)
                    (.setSubscription subscription-name)
                    (.addAllAckIds ack-ids)
                    .build)]
    (when (not-empty ack-ids)
      (-> subscriber
          .acknowledgeCallable
          (.call ack-req)))))

(defn- rcv-msg->msg
  [^ReceivedMessage rcv-msg]
  (let [^PubsubMessage msg (.getMessage rcv-msg)
        payload (-> msg
                    .getData
                    .toStringUtf8
                    (json/parse-string true))
        ; clojure.walk/keywordize-keys doesn't work with Java Maps!
        attributes (into {} (for [[k v] (.getAttributesMap msg)] [(keyword k) v]))]
    (assoc payload :pubsub/attributes attributes
           :pubsub/ack-id (.getAckId rcv-msg))))

(defn ack?
  "Returns whether or not to ACK a result or not.
   Successful processing will always result in a ACK.
   Unsuccessful items will be ACK-ed *if* we do not wish to retry processing at a later time"
  [{:keys [ok? retry?] :or {ok? true retry? true}}]
  (or ok? (not retry?)))

(defn- pull&process&ack
  "Pull messages from `subscriber` on `subscription-name`, route them through
  `handler`. Always `ack`."
  [{:keys [handler metrics-registry pull-max-messages]
    :or {pull-max-messages 1}
    :as _opts}
   ^SubscriberStub subscriber
   ^String subscription-name]
  (let [rcv-msgs (pull-msgs subscriber subscription-name pull-max-messages)
        _        (prometheus/inc metrics-registry ::message-count {:state :received} (count rcv-msgs))
        msgs     (map rcv-msg->msg rcv-msgs)
        results  (handler msgs)
        to-ack   (filter ack? results)]
    (prometheus/inc metrics-registry ::message-count {:state :processed} (count results))
    ;; NOTE There is not explicit way to NACK items with the Sync Pull client, therefore we only ACK items
    (ack-msgs subscriber subscription-name to-ack)
    (prometheus/inc metrics-registry ::message-count {:state :acked} (count to-ack))))

(defn- forever-do
  "Executes `f` forever, presumably for side effects.
  Returns a `fn` that stops the loop when invoked."
  [f]
  (let [fut (future (while true (f)))]
    (fn []
      (future-cancel fut))))

(defn start-subscriber
  [{:keys [project-id subscription-id] :as opts}]
  (let [subscriber-settings (-> (SubscriberStubSettings/newBuilder)
                                .build)
        subscriber          (GrpcSubscriberStub/create subscriber-settings)
        subscription-name   (ProjectSubscriptionName/format project-id
                                                            subscription-id)
        loop-f              (fn []
                              (try (pull&process&ack opts subscriber subscription-name)
                                   ;; TODO revisit how we handle exceptions
                                   ;; here, maybe we need to restart the
                                   ;; service in some cases?
                                   (catch DeadlineExceededException ex
                                     (log/info ex
                                               "Caught DeadlineExceededException in PubSub subscriber loop. Will continue."))
                                   (catch Exception ex
                                     (log/error ex
                                                "Caught exception in PubSub subscriber loop. Will ignore and continue unfazed."))))
        stop-loop           (forever-do loop-f)
        stop-subscriber-f   (fn []
                              (stop-loop)
                              (.shutdownNow subscriber)
                              (.awaitTermination subscriber 5 TimeUnit/SECONDS)
                              :halted)]
    stop-subscriber-f))

(defn ->healthcheck
  [{:keys [project-id subscription-id], :as _pub-sub-subscriber-opts}]
  (fn []
    (let [sub (try (pub-sub-admin/get-subscription project-id subscription-id)
                   (catch Exception ex
                     (log/error ex "PubSub healthcheck failed")
                     nil))]
      {:name "pub-sub", :healthy? (boolean sub)})))

(defmethod ig/pre-init-spec ::subscriber
  [_]
  ::subscriber.opts)

(defmethod ig/init-key ::subscriber
  [_ opts]
  (start-subscriber opts))

(defmethod ig/halt-key! ::subscriber
  [_ stop-subscriber]
  stop-subscriber)

(defmethod ig/pre-init-spec ::subscriber.healthcheck
  [_]
  ::subscriber.healthcheck.opts)

(defmethod ig/init-key ::subscriber.healthcheck
  [_ opts]
  (->healthcheck opts))
