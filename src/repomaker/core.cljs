(ns repomaker.core
  (:require [repomaker.github :as github]
            [repomaker.dockerhub :as dockerhub]
            [repomaker.config :as config]
            [cljs.nodejs :as nodejs]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [<! >! close! chan]]
    ; FIXME
            [repomaker.dev :as devvv]
            [cljs.tools.cli :as cli]
            [clojure.string :as string]
            [cljs-callback-heaven.core :refer [<print >?]])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

(nodejs/enable-util-print!)

(defn env [name]
  (aget process.env name))

(when (env "SM")
  (try
    (.install (nodejs/require "source-map-support"))
    (catch js/Error e :ignore)))

(def mandatory-args #{:repo :type})
(defn args-missing? [args]
  (loop [valid false
         remaining-opts mandatory-args]
    (let [opt (first remaining-opts)]
      (cond
        (empty? (rest remaining-opts)) false
        ;; FIXME Maybe it is possible to simplify using `mandatory-args` as function
        (nil? (opt args)) (do (println (str "missing cli arg --" (name opt)))
                              true)
        :else (recur false (rest remaining-opts))))))


(defn incorrect-type? [types ^keyword proj-type]
  (when (nil? (-> types (proj-type)))
    (throw (new js/Error (str "Unknown project type: '" (name proj-type) "'")))))

(def cli-options
  [["-r" "--repo NAME" "Repo name"
    :validate [#(re-matches #"^[0-9a-zA-Z-_]+$" %) "Must consist of alpha-numeric and '_', '-' characters"]]
   ["-t" "--type TYPE" "Project type"
    :parse-fn keyword]
   ["-c" "--confd DIR" "Configuration directory. Defaults to $HOME/.repomaker"
    :default (str (env "HOME") "/.repomaker")]
   ["-p" "--providers LIST" "Providers to use. Defaults to github,dockerhub"
    :default #{"github" "dockerhub"}
    :parse-fn #(do (println %)
                   (set (string/split % ",")))]
   ["-n" "--dry-run" "Do not really execute, just print what would happen"]
   ["-h" "--help"]])


(defn -main [& args]
  (when (nil? (env "NOP"))
    (let [{:keys [options errors summary]} (cljs.tools.cli/parse-opts args cli-options)
          {:keys [types credentials]} (config/read-dir (:confd options))
          proj-type (:type options)
          repo-name (:repo options)
          providers (:providers options)]
      (if (or (:help options)
              (args-missing? options)
              (incorrect-type? types proj-type))
        (println (str (string/join \newline errors) "\n\nOptions summary:\n\n" summary))
        (when-not (:dry-run options)
          (when (contains? providers "github")
            (println "github")
            (github/setup (get-in types [proj-type :github :org])
                          repo-name
                          (or (env "GH_USER") (get-in credentials [:github :user]))
                          (or (env "GH_PASS") (get-in credentials [:github :pass]))
                          (get-in types [proj-type :github :teams])
                          (get-in types [proj-type :github :private] true)))
          (when (contains? providers "dockerhub")
            (dockerhub/setup (get-in types [proj-type :dockerhub :org])
                             repo-name
                             (or (env "DH_USER") (get-in credentials [:dockerhub :user]))
                             (or (env "DH_PASS") (get-in credentials [:dockerhub :pass]))
                             (get-in types [proj-type :dockerhub :teams])
                             (get-in types [proj-type :dockerhub :private] true))))))))

(set! *main-cli-fn* -main)

