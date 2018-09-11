(defproject ovotech/clj-gcp "0.1.0"
  :description "Clojure utilities for the Google Cloud Platform"

  :url "https://github.com/ovotech/clj-gcp"
  :license {:name "Eclipse Public License"
            :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [;;;

                 [cheshire "5.8.0"]
                 [com.google.cloud/google-cloud-pubsub "1.40.0"]
                 [iapetos "0.1.8"]
                 [integrant "0.6.3"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.2.168"]
                 [org.clojure/tools.logging "0.4.1"]

                 ;;;
                 ]

  :test-selectors {:default     (complement :integration)
                   :integration :integration}

  :profiles {:dev {:dependencies [[expound "0.7.1"]]}
             :ci  {:deploy-repositories
                   [["clojars" {:url           "https://clojars.org/repo"
                                :username      :env ;; LEIN_USERNAME
                                :password      :env ;; LEIN_PASSWORD
                                :sign-releases false}]]}})
