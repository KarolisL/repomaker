(ns repomaker.promises
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [>! close!]])
  (:use-macros [cljs.core.async.macros :only [go]]))

(defn put&close [ch context]
  (fn [data] (go (>! ch [data context])
                 (close! ch))))

