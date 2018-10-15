(ns clj-gcp.common.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]))

(s/def ::non-blank-string
  (s/and string? (complement string/blank?)))
