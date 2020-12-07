(defproject ovotech/clj-gcp "0.6.14"

  :description "Clojure utilities for the Google Cloud Platform"

  :url "https://github.com/ovotech/clj-gcp"
  :license {:name "Eclipse Public License"
            :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [;;;

                 [cheshire "5.10.0"]
                 [clj-commons/fs "1.5.2"]
                 [com.google.cloud/google-cloud-pubsub "1.110.0"]
                 [com.google.oauth-client/google-oauth-client "1.31.2"]
                 [com.google.cloud/google-cloud-storage "1.113.4"
                  :exclusions [com.google.oauth-client/google-oauth-client]]
                 [digest "1.4.9"]
                 [iapetos "0.1.8"]
                 [integrant "0.8.0"]
                 [medley "1.3.0"]
                 [metosin/ring-http-response "0.9.1"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/spec.alpha "0.2.187"]
                 [org.clojure/tools.logging "1.1.0"]]

                 ;;;


  :test-selectors {:default     (complement :integration)
                   :integration :integration}

  :profiles {:dev {:dependencies [[expound "0.8.7"]]
                   :plugins [[lein-ancient "0.6.15"]]}
             :ci  {:deploy-repositories
                   [["clojars" {:url           "https://clojars.org/repo"
                                :username      :env ;; LEIN_USERNAME
                                :password      :env ;; LEIN_PASSWORD
                                :sign-releases false}]]}})
