# clj-gcp [![CircleCI](https://circleci.com/gh/ovotech/clj-gcp/tree/master.svg?style=svg)](https://circleci.com/gh/ovotech/clj-gcp/tree/master)

Clojure utilities for the Google Cloud Platform.


## Usage

[![Clojars Project](https://img.shields.io/clojars/v/ovotech/clj-gcp.svg)](https://clojars.org/ovotech/clj-gcp)

Mainly used through integrant:

```clojure
;; ig-config.edn
:clj-gcp.pub-sub.core/subscriber
{:metrics-registry     #ig/ref :metrics/registry ;; a iapetos metric registry
 :handler              #ig/ref :pubsub/handler ;; a fn, described below
 :project-id                   "my-gcp-project"
 :topic-id                     "industry-data-lake-file-notifications"
 :pull-max-messages            10
 :subscription-id              "LOCAL_DEV.bucket-notifications.my-service"}
```

The function `:handler` takes **a seq of maps that contain**:
```clojure
{,,, pub-sub message fields (always deserialized as JSON) ,,,
 :pubsub/attributes {:eventType "OBJECT_FINALIZE"}
 :pubsub/ack-id     "0000000ACK"}
```
... and should return **a seq of maps that contain at least**:
```clojure
;; these will be acked:
{:pubsub/ack-id     "0000000ACK"
 :ok? true}
{:pubsub/ack-id     "0000000ACK"
 :ok? false
 :retry? false}

;; this will be nacked:
{:pubsub/ack-id     "0000000ACK"
 :ok? false
 :retry? true}
```


### Healthcheck

There's also a healthcheck integrant key available:

```clojure
;; ig-config.edn
:clj-gcp.pub-sub.core/subscriber.healthcheck
{:project-id     "my-gcp-project"
 :subscription-id "LOCAL_DEV.bucket-notifications.my-service"}
```


## Running Tests

```bash
GCP_PROJECT_ID=my-gcp-project lein test :integration
```


## License

Copyright © 2018 OVO Energy Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
