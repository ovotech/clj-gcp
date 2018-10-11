(ns clj-gcp.storage.core
  (:require [clj-gcp.common.specs :as cs]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            digest
            [integrant.core :as ig]
            [me.raynes.fs :as fs]
            [medley.core :as m])
  (:import [com.google.cloud.storage Blob$BlobSourceOption BlobId BlobInfo Storage Storage$BlobGetOption Storage$BlobWriteOption Storage$BucketListOption StorageOptions]
           java.nio.channels.Channels))

(defprotocol StorageClient
  (get-blob [this bucket-name blob-name])
  (blob-writer [this bucket-name blob-name opts]))

;;,------
;;| Blobs
;;`------

(s/def ::md5Hash ::cs/non-blank-string) ; TODO more restrictive
(s/def ::inputStream #(instance? java.io.InputStream %))
(s/def ::createdAt integer?)
(s/def ::contentType
  #{"text/plain"
    "application/json"
    "application/octet-stream"
    "binary/octet-stream"
    "avro/binary"})
(s/def ::contentEncoding #{"utf-8"})
(s/def :blob/metadata (s/map-of string? string?))
(s/def ::source ::cs/non-blank-string)
(s/def ::blob
  (s/keys :req-un [::inputStream ::md5Hash ::contentType ::createdAt ::source]
          :opt-un [::contentEncoding :blob/metadata]))

;;,---------------
;;| GCSStorageClient
;;`---------------
(defn- gcs-get-blob
  [^Storage gservice bucket-name blob-name]
  (if-let [blob (.get gservice
                      bucket-name
                      blob-name
                      (make-array Storage$BlobGetOption 0))]
    (let [inputStream (-> blob
                          (.reader (make-array Blob$BlobSourceOption 0))
                          Channels/newInputStream)]
      ;; TODO add md5 check on inputStream with decorator?
      ;; https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
      (->> (m/assoc-some {:inputStream inputStream,
                          :md5Hash     (.getMd5 blob),
                          :createdAt   (.getCreateTime blob),
                          :contentType (.getContentType blob),
                          :source      (format "gs://%s/%s" bucket-name blob-name)}
                         :contentEncoding (.getContentEncoding blob)
                         :metadata (into {} (.getMetadata blob)))
           (s/assert ::blob)))
    (throw (ex-info "no such blob"
                    {:blob-name blob-name, :bucket-name bucket-name}))))
(defn- gcs-blob-writer
  ([gservice bucket-name blob-name]
   (gcs-blob-writer gservice bucket-name blob-name nil))
  ([^Storage gservice bucket-name blob-name
    {:keys [metadata contentType contentEncoding]}]
   (let [blob-info (-> (BlobInfo/newBuilder (BlobId/of bucket-name blob-name))
                       (cond-> contentEncoding (.setContentEncoding
                                                contentEncoding)
                               metadata (.setMetadata metadata)
                               contentType (.setContentType contentType))
                       (.build))
         nio-writer
         (.writer gservice blob-info (make-array Storage$BlobWriteOption 0))]
     nio-writer)))

(defrecord GCSStorageClient [^Storage gservice]
  StorageClient
  (get-blob [this bucket-name blob-name]
    (gcs-get-blob gservice bucket-name blob-name))
  (blob-writer [this bucket-name blob-name opts]
    (gcs-blob-writer gservice bucket-name blob-name opts)))
(alter-meta! #'->GCSStorageClient assoc :private true)

(defn gcs-healthcheck
  []
  (let [healthy? (try (-> (.getService (StorageOptions/getDefaultInstance))
                          (.list (make-array Storage$BucketListOption 0))
                          boolean)
                      (catch Exception ex
                        (log/error ex "gstore healthcheck failed")
                        false))]
    {:name "google-storage", :healthy? healthy?}))

(defn ->gcs-storage-client
  ([]
   (-> (StorageOptions/getDefaultInstance)
       .getService
       ->GCSStorageClient))
  ([project-id]
   (-> (StorageOptions/newBuilder)
       (.setProjectId project-id)
       .getService
       ->GCSStorageClient)))

(defmethod ig/init-key :common.clients.storage/client
  [_ _]
  (->gcs-storage-client))
(defmethod ig/init-key :common.clients.storage/client.healthcheck
  [_ _]
  gcs-healthcheck)

;;,------------------------
;;| FileSystemStorageClient
;;`------------------------

(defn- mkdirs [file] (fs/mkdirs (.getParent file)))
(defn- fs-blob-info-file
  [base-path bucket-name blob-name]
  (let [;; HACK to support blob-names like "foo/bar/baz.txt"
        blob-namef (io/file blob-name)
        infof-name (str (some-> (.getParent blob-namef)
                                (str "/"))
                        (format ".%s.info.edn" (.getName blob-namef)))]
    (fs/normalized (io/file base-path bucket-name infof-name))))
(defn- fs-blob-info
  [base-path bucket-name blob-name]
  (merge {:contentType "application/octet-stream"}
         (let [infof (fs-blob-info-file base-path bucket-name blob-name)]
           (when (fs/exists? infof)
             (-> infof
                 io/reader
                 java.io.PushbackReader.
                 edn/read)))))
(defn- fs-blob-file
  [base-path bucket-name blob-name]
  (io/file base-path bucket-name blob-name))
(defn fs-get-blob
  [base-path bucket-name blob-name]
  (let [blobf (fs-blob-file base-path bucket-name blob-name)]
    (if (fs/exists? blobf)
      (merge {:inputStream (io/input-stream blobf),
              :md5Hash (digest/md5 (str blobf)), ;; HACK
              :createdAt (.lastModified blobf),
              :source (str (fs/normalized blobf))}
             (fs-blob-info base-path bucket-name blob-name))
      (throw (ex-info "no such blob"
                      {:blob-name blob-name,
                       :bucket-name bucket-name,
                       :base-path base-path,
                       :blob-file blobf})))))
(defn fs-blob-writer
  ([base-path bucket-name blob-name]
   (fs-blob-writer base-path bucket-name blob-name nil))
  ([base-path bucket-name blob-name opts]
   (let [blobf (fs-blob-file base-path bucket-name blob-name)
         infof (fs-blob-info-file base-path bucket-name blob-name)]
     (mkdirs blobf)
     (mkdirs infof)
     (spit infof opts)
     (->> blobf
          io/output-stream
          Channels/newChannel))))

(defrecord FileSystemStorageClient [base-path]
  StorageClient
  (get-blob [_ bucket blob-name] (fs-get-blob base-path bucket blob-name))
  (blob-writer [_ bucket blob-name opts]
    (fs-blob-writer base-path bucket blob-name opts)))
(alter-meta! #'->FileSystemStorageClient assoc :private true)

(defn ->file-system-storage-client
  [base-path]
  (->FileSystemStorageClient base-path))
