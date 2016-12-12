(ns repomaker.core
  (:require [repomaker.github :as github]
            [repomaker.dockerhub :as dockerhub]
            [cljs.nodejs :as nodejs]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [<! >! close! chan]]
            [clojure.string :as string]
            [cljs-callback-heaven.core :refer [<print >?]])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

(nodejs/enable-util-print!)

(defn parse-set [js-str]
  (set (js->clj (.split js-str ","))))

(def commander (nodejs/require "commander"))

(doto commander
  (.version "0.0.1")
  (.option "-r, --repo <name>" "name of new repo")
  (.option "-t, --type <name>" "type of project")
  (.option "-c, --confd <dir>" "configuration directory",
           (str (aget process.env "HOME") "/.repomaker"))
  (.option "-p, --provider [provider,provider]" "comma-separated list of providers
           defaults to github,dockerhub" parse-set #{"github" "dockerhub"})
  (.parse process.argv))



(def mandatory-opts ["repo" "type"])
(defn validate-args [opts]
  (doseq [opt mandatory-opts]
    (when (nil? (aget opts opt))
      (throw (new js/Error (str "missing cli argument --" opt))))))

(defn read-edn-file [dir filename]
  (let [fs (nodejs/require "fs")]
    (reader/read-string (str (.readFileSync fs (str dir "/" filename))))))

(defn read-config [dir]
  (let [fs (nodejs/require "fs")]
    {:credentials (read-edn-file dir "credentials.edn")
     :types       (read-edn-file dir "teams.edn")}))

(defn env [name]
  (aget process.env name))

(defn user [config ^keyword provider]
  (-> config
      (:credentials)
      (provider)
      :user))

(defn pass [config ^keyword provider]
  (-> config
      (:credentials)
      (provider)
      :pass))


(defn org [config ^keyword provider ^keyword proj-type]
  (-> config
      (:types)
      (proj-type)
      (provider)
      (:org)))

(defn teams [config ^keyword provider ^keyword proj-type]
  (-> config
      (:types)
      (proj-type)
      (provider)
      (:teams)))


(defn validate-type [config ^keyword proj-type]
  (when (nil? (-> config (:types) (proj-type)))
    (throw (new js/Error (str "Unknown project type: '" proj-type "'")))))

(defn -main [& args]
  (when-not (env "NOP")
    (let [config (read-config (aget commander "confd"))
          gh-user (or (env "GH_USER") (user config :github))
          gh-pass (or (env "GH_PASS") (pass config :github))
          dh-user (or (env "DH_USER") (user config :dockerhub))
          dh-pass (or (env "DH_PASS") (pass config :dockerhub))
          proj-type (keyword (aget commander "type"))
          repo-name (aget commander "repo")
          providers (aget commander "provider")]
      (validate-args commander)
      (validate-type config proj-type)
      (when (contains? providers "github")
        (github/setup (org config :github proj-type) repo-name gh-user gh-pass (teams config :github proj-type)))
      (when (contains? providers "dockerhub")
        (dockerhub/setup (org config :dockerhub proj-type) repo-name dh-user dh-pass (teams config :dockerhub proj-type))))))

(set! *main-cli-fn* -main)

