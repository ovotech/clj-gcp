(ns clj-gcp.integration.pub-sub.utils
  (:import com.google.cloud.pubsub.v1.Publisher
           com.google.protobuf.ByteString
           [com.google.pubsub.v1 ProjectTopicName PubsubMessage]))

(defn pubsub-publish
  ([message project topic]
   (pubsub-publish message {} project topic))
  ([message attributes project topic]
   (let [publisher (-> (ProjectTopicName/of project topic)
                       Publisher/newBuilder
                       .build)
         data (-> message
                  ByteString/copyFromUtf8)
         message (-> (PubsubMessage/newBuilder)
                     (.putAllAttributes attributes)
                     (.setData data)
                     .build)]
     (try
       ;; NOTE .get awaits for the future to complete
       (.get (.publish publisher message))
       (finally (when publisher (.shutdown publisher)))))))
