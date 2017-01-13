(ns dev.repl
  (:require [figwheel-sidecar.repl-api :as sidecar-repl]
            cemerick.piggieback
            cljs.repl.node))

(defn launch-cljs-repl
  []
  (cemerick.piggieback/cljs-repl (cljs.repl.node/repl-env)))

(launch-cljs-repl)
