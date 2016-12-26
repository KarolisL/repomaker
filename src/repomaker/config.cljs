(ns repomaker.config
  (:require [cljs.tools.reader.edn :as reader]
            [cljs.nodejs :as nodejs]))

(defn read-edn-file [dir filename]
  (let [fs (nodejs/require "fs")]
    (reader/read-string (str (.readFileSync fs (str dir "/" filename))))))

(defn read-dir [dir]
  (let [fs (nodejs/require "fs")]
    {:credentials (read-edn-file dir "credentials.edn")
     :types       (read-edn-file dir "teams.edn")}))


