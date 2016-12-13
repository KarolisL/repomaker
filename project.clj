(defproject dynamodb-backup "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :min-lein-version "2.5.3"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [figwheel-sidecar "0.5.2"]
                 [org.bodil/pylon "0.3.0"]
                 [org.clojure/core.async "0.2.395"]
                 [com.cemerick/piggieback "0.2.1"]
                 [mount "0.1.10"]]

  :plugins [[lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-2"]
            [lein-doo "0.1.6"]]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.10"]]

                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}

  :source-paths ["src"]

  :clean-targets ["server.js"
                  "target"]

  :cljsbuild {
              :builds [{:id           "dev"
                        :source-paths ["src"]
                        :figwheel     true
                        :compiler     {
                                       :main          repomaker.core
                                       :output-to     "target/server_dev/repomaker.js"
                                       :output-dir    "target/server_dev"
                                       :target        :nodejs
                                       :optimizations :none
                                       :language-in   :ecmascript6
                                       :language-out  :ecmascript5
                                       :source-map    true}}
                       {:id           "prod"
                        :source-paths ["src"]
                        :compiler     {
                                       :output-to     "bin/repomaker.js"
                                       :output-dir    "target/server_prod"
                                       :target        :nodejs
                                       :language-in   :ecmascript6
                                       :language-out  :ecmascript5
                                       :optimizations :simple}}
                       {:id           "test-none"
                        :source-paths ["src" "test"]
                        :compiler     {:optimizations :none
                                       :target        :nodejs
                                       :language-in   :ecmascript6
                                       :output-dir    "out-test-none"
                                       :output-to     "target/repomaker-test-none.js"
                                       :externs       ["externs.js"]
                                       :verbose       true
                                       :pretty-print  true}}]}
  :figwheel {
             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7888
             }
  :doo {:build "test-none"})

