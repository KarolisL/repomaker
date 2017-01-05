(defproject repomaker "0.2.4"
  :description "Create repositories in github and dockerhub"
  :url "http://example.com/FIXME"

  :min-lein-version "2.5.3"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/tools.cli "0.3.5"]
                 [figwheel-sidecar "0.5.2"]
                 [org.bodil/pylon "0.3.0"]
                 [org.clojure/core.async "0.2.395"]
                 [com.cemerick/piggieback "0.2.1"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-figwheel "0.5.0-2"]
            [org.clojars.karolisl/lein-npm "0.6.3-SNAPSHOT"]
            [lein-doo "0.1.6"]
            [lein-shell "0.5.0"]]

  :npm {
        :repository      "github:KarolisL/repomaker"
        :license         "EPL-1.0"
        :private         false
        :bin             {:repomaker "./bin/repomaker.js"}
        :dependencies    [[docker-hub-api "0.5.1"]
                          [axios "0.15.3"]]
        :devDependencies [[source-map-support "0.4.0"]
                          ; For Figwheel
                          [ws "1.1.1"]]}

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
                                       :output-to     "target/dev/repomaker.js"
                                       :output-dir    "target/dev"
                                       :target        :nodejs
                                       :optimizations :none
                                       :language-in   :ecmascript6
                                       :language-out  :ecmascript5
                                       :source-map    true
                                       :pretty-print  true}}
                       {:id           "prod"
                        :source-paths ["src"]
                        :compiler     {
                                       :output-to     "target/prod/repomaker.js"
                                       :output-dir    "target/prod"
                                       :target        :nodejs
                                       :language-in   :ecmascript6
                                       :language-out  :ecmascript5
                                       :optimizations :simple
                                       :pretty-print  true
                                       :verbose       true
                                       :source-map    "target/prod/repomaker.js.map"}}
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
  :figwheel {:nrepl-port 7888}
  :doo {:build "test-none"}
  :aliases {"copy-prod-release"
                      ["do"
                       ["shell" "cp" "target/prod/repomaker.js" "target/prod/repomaker.js.map" "bin/"]]
            "build-prod" ["do"
                       ["cljsbuild" "once" "prod"]
                       "copy-prod-release"]
            "publish" ["do"
                       "build-prod"
                       ["npm" "publish"]]})

