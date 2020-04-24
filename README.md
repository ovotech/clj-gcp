# clj-gcp [![CircleCI](https://circleci.com/gh/ovotech/clj-gcp/tree/master.svg?style=svg)](https://circleci.com/gh/ovotech/clj-gcp/tree/master)


Clojure utilities for the Google Cloud Platform.


[![Clojars Project](https://img.shields.io/clojars/v/ovotech/clj-gcp.svg)](https://clojars.org/ovotech/clj-gcp)
[![Code Coverage](https://codecov.io/gh/ovotech/clj-gcp/branch/master/graph/badge.svg)](https://codecov.io/gh/ovotech/clj-gcp)
## Usage

### Pub-Sub
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


#### Healthcheck

There's also a healthcheck integrant key available:

```clojure
;; ig-config.edn
:clj-gcp.pub-sub.core/subscriber.healthcheck
{:project-id     "my-gcp-project"
 :subscription-id "LOCAL_DEV.bucket-notifications.my-service"}
```

### Storage

```clojure
(let [sut                 (sut/->gcs-storage-client)
      blob-name           (format "TEST_BLOBS/BLOB-%s.txt" (m/random-uuid))
      exp-contentEncoding "utf-8"
      exp-content         "hello!"
      exp-metadata        {"hi" "foo"}
      exp-contentType     "text/plain"]

  ;; This is how you write a blob:
  (with-open [out (Channels/newWriter
                   (sut/blob-writer sut
                                    bucket-name
                                    blob-name
                                    {:metadata        exp-metadata,
                                     :contentType     exp-contentType,
                                     :contentEncoding exp-contentEncoding})
                   exp-contentEncoding)]
    (.write out exp-content))

  ;; ... and this is how you get it back
  (let [{:keys [inputStream md5Hash contentType source
                contentEncoding metadata],
         :as   got}
        (sut/get-blob sut bucket-name blob-name)]
    (tu/is-valid ::sut/blob got)
    (is (= (str "gs://" bucket-name "/" blob-name) source))
    (is (= exp-contentType contentType))
    (is (= exp-contentEncoding contentEncoding))
    (is (= exp-metadata metadata))
    (is (= exp-content (slurp inputStream)))))
```

Can also be used as an Integrant component:

```
;; ig-config.edn
:clj-gcp.storage/client             nil
:clj-gcp.storage/client.healthcheck nil
```

#### Testing implementation

A test implementation based on the file system is available:

```clojure
(sut/->file-system-storage-client base-path)
```


## Running Tests

```bash
GCP_PROJECT_ID=my-gcp-project lein test :integration
```


## License

Copyright © 2018 OVO Energy Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
