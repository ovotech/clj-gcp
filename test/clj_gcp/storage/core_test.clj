(ns clj-gcp.storage.core-test
  (:require [clj-gcp.storage.core :as sut]
            [clj-gcp.test-utils :as tu]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [medley.core :as m])
  (:import java.nio.channels.Channels))

(deftest fs-get-blob-test
  (let [sut (sut/->file-system-storage-client ".")]
    (testing "getting a blob"
             (let [{:keys [inputStream md5Hash contentType source
                           contentEncoding metadata],
                    :as got}
                     (sut/get-blob sut "." "README.md")]
               (tu/is-valid ::sut/blob got)
               (is (= (.getAbsolutePath (io/file "README.md")) source))
               (is (= "application/octet-stream" contentType))
               (is (nil? contentEncoding))))
    (testing "getting non-existing blob"
             (is (thrown? clojure.lang.ExceptionInfo
                          (sut/get-blob sut "." "I_DO_NOT_EXIST.md"))))))

(deftest fs-copy-blob-test
  (let [sut (sut/->file-system-storage-client ".")]
    (testing "getting a blob"
      (let [from-blob (sut/get-blob sut "." "README.md")
            _ (sut/copy-blob sut ["." "README.md"] ["." "README.md.new"])
            to-blob (sut/get-blob sut "." "README.md.new")]
        (tu/is-valid ::sut/blob from-blob)
        (is (= (.getAbsolutePath (io/file "README.md")) (:source from-blob)))
        (is (= (.getAbsolutePath (io/file "README.md.new")) (:source to-blob)))
        (is (= (:md5Hash from-blob) (:md5Hash to-blob)))
        (is (= (:contentEncoding from-blob) (:contentEncoding to-blob)))
        (io/delete-file "README.md.new")))))

(deftest fs-move-blob-test
  (let [sut (sut/->file-system-storage-client ".")]
    (testing "getting a blob"
      (let [from-blob (sut/get-blob sut "." "README.md")
            _ (sut/copy-blob sut ["." "README.md"] ["." "README.md.cp"])
            _ (sut/move-blob sut ["." "README.md.cp"] ["." "README.md.mv"])
            to-blob (sut/get-blob sut "." "README.md.mv")]
        (tu/is-valid ::sut/blob from-blob)
        (is (= (.getAbsolutePath (io/file "README.md")) (:source from-blob)))
        (is (= (.getAbsolutePath (io/file "README.md.mv")) (:source to-blob)))
        (is (= (:md5Hash from-blob) (:md5Hash to-blob)))
        (is (= (:contentEncoding from-blob) (:contentEncoding to-blob)))
        (is (thrown? clojure.lang.ExceptionInfo
                     (sut/get-blob sut "." "README.md.cp")))
        (io/delete-file "README.md.mv")))))

(deftest fs-blob-writer-test
  (let [base-path (fs/normalized "/tmp/test-blobs")
        bucket    "foo-bucket"
        blob-name (format "%s/%s.txt" (m/random-uuid) (m/random-uuid))

        sut (sut/->file-system-storage-client base-path)
        exp-contentEncoding "utf-8"
        exp-contentType "text/plain"
        exp-content "mandi!"
        exp-metadata {"hi" "foo"}
        opts {:metadata exp-metadata,
              :contentType exp-contentType,
              :contentEncoding exp-contentEncoding}]
    (with-open [out (Channels/newWriter
                      (sut/blob-writer sut bucket blob-name opts)
                      exp-contentEncoding)]
      (.write out exp-content))
    (let [{:keys [inputStream md5Hash contentType source
                  contentEncoding metadata],
           :as got}
            (sut/get-blob sut bucket blob-name)]
      (tu/is-valid ::sut/blob got)
      (is (= (.getAbsolutePath (io/file base-path bucket blob-name)) source))
      (is (= exp-contentType contentType))
      (is (= exp-contentEncoding contentEncoding))
      (is (= exp-metadata metadata))
      (is (= exp-content (slurp inputStream))))))
