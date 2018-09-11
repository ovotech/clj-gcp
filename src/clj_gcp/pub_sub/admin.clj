(ns clj-gcp.pub-sub.admin
  "NOTE functions here may trigger Admin request quotas
  https://cloud.google.com/pubsub/docs/admin"
  (:import [com.google.cloud.pubsub.v1 SubscriptionAdminClient TopicAdminClient]
           com.google.cloud.ServiceOptions
           [com.google.pubsub.v1 ProjectSubscriptionName ProjectTopicName PushConfig]))

(defn get-default-project-id [] (ServiceOptions/getDefaultProjectId))

(defn create-topic
  ([topic-id] (create-topic (get-default-project-id) topic-id))
  ([project-id topic-id]
   (with-open [tac (TopicAdminClient/create)]
     (let [tn (ProjectTopicName/of project-id topic-id)]
       (.createTopic tac tn)))))

(defn delete-topic
  ([topic-id] (delete-topic (get-default-project-id) topic-id))
  ([project-id topic-id]
   (with-open [tac (TopicAdminClient/create)]
     (let [tn (ProjectTopicName/of project-id topic-id)]
       (.deleteTopic tac tn)))))

(defn create-subscription
  "Creates a subscription.

  Use 0 for default ack deadline seconds (10s for now).
  NOTE subscription ids are unique across a project"
  ([topic-id subscription-id ack-deadline-seconds]
   (create-subscription (get-default-project-id)
                        topic-id
                        subscription-id
                        ack-deadline-seconds))
  ([project-id topic-id subscription-id ack-deadline-seconds]
   (with-open [sac (SubscriptionAdminClient/create)]
     (let [tn (ProjectTopicName/of project-id topic-id)
           sn (ProjectSubscriptionName/of project-id subscription-id)]
       (.createSubscription sac
                            sn
                            tn
                            (PushConfig/getDefaultInstance)
                            ack-deadline-seconds)))))

(defn get-subscription
  ([subscription-id]
   (get-subscription (get-default-project-id) subscription-id))
  ([project-id subscription-id]
   (with-open [sac (SubscriptionAdminClient/create)]
     (let [sn (ProjectSubscriptionName/of project-id subscription-id)]
       (.getSubscription sac sn)))))

(defn delete-subscription
  "Deletes a subscription.

  NOTE subscription ids are unique across a project"
  ([subscription-id]
   (delete-subscription (get-default-project-id) subscription-id))
  ([project-id subscription-id]
   (with-open [sac (SubscriptionAdminClient/create)]
     (let [sn (ProjectSubscriptionName/of project-id subscription-id)]
       (.deleteSubscription sac sn)))))
