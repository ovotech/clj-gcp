(ns clj-gcp.integration.storage.core-test
  (:require [clj-gcp.common.health :as health]
            [clj-gcp.storage.core :as sut]
            [clj-gcp.test-utils :as tu]
            [clojure.test :refer :all]
            [medley.core :as m])
  (:import java.nio.channels.Channels))

(def bucket-name "flow-platform-test-blobs")
(defn gcp-project-id [] (System/getenv "GCP_PROJECT_ID"))

(deftest ^:integration healthcheck
  (let [healthcheck (sut/gcs-healthcheck)]
    (tu/is-valid ::health/healthcheck healthcheck)
    (is (:healthy? healthcheck))))

(deftest ^:integration gcs-blob-e2e-test
  (let [sut                 (sut/->gcs-storage-client (gcp-project-id))
        blob-name           (format "TEST_BLOBS/BLOB-%s.txt" (m/random-uuid))
        exp-contentEncoding "utf-8"
        exp-content         "mandi!"
        exp-metadata        {"hi" "foo"}
        exp-contentType     "text/plain"]
    (testing "writing blob"
      (is (not (sut/exists sut bucket-name blob-name)))
      (with-open [out (Channels/newWriter
                       (sut/blob-writer sut
                                        bucket-name
                                        blob-name
                                        {:metadata        exp-metadata
                                         :contentType     exp-contentType
                                         :contentEncoding exp-contentEncoding})
                       exp-contentEncoding)]
        (.write out exp-content))
      (is (sut/exists sut bucket-name blob-name)))

    (testing "getting blob"
      (let [{:keys [inputStream md5Hash contentType source
                    contentEncoding metadata],
             :as   got}
            (sut/get-blob sut bucket-name blob-name)]
        (tu/is-valid ::sut/blob got)

        (is (= (str "gs://" bucket-name "/" blob-name) source))
        (is (= exp-contentType contentType))
        (is (= exp-contentEncoding contentEncoding))
        (is (= exp-metadata metadata))
        (is (= exp-content (slurp inputStream)))))))

(deftest ^:integration gcs-get-non-existing-blob-test
  (let [sut (sut/->gcs-storage-client (gcp-project-id))]
    (is (thrown?
         clojure.lang.ExceptionInfo
         (sut/get-blob sut bucket-name "I_DO_NOT_EXIST.md")))))
