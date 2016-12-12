(ns repomaker.github
  (:require [cljs.core.async :as async]
            [cljs.core.async :as async :refer [<! >! close! chan]]
            [cljs.nodejs :as nodejs])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))


(defn gh-callback
  [name ch err status body headers]
  (go (>! ch [err status body headers name])
      (close! ch)))


(defn format-error [repo err]
  (let [body (js->clj (aget err "body"))
        status (js->clj (aget err "statusCode"))]
    (str err body status)))

(def abort-on-error? true)
(defn http-error-msg [err]
  (:message (first (js->clj (aget err "body" "errors") :keywordize-keys true))))

(defn- repo-already-exists? [err]
  (and err
       (= (aget err "message") "Validation Failed")
       (= (http-error-msg err) "name already exists on this account")))

(defn create-repo [github org repo]
  (let [finished-ch (chan)
        out-ch (chan)]
    (.post github (str "/orgs/" org "/repos")
           #js {:repo repo :private true}
           (partial gh-callback repo finished-ch))
    (go (let [[err status body] (<! finished-ch)]
          (cond
            (repo-already-exists? err)
            (do
              (>! out-ch :already-exists)
              (println (str "github: repo '" repo "'already exists")))

            err
            (do
              (>! out-ch :failure)
              (println (str "github: error creating repo '" repo "': " (format-error repo err))))

            :else (do (>! out-ch :success)
                      (println (str "github: repo succesfully created"))))))
    out-ch))



(defn add-team [github org repo-name {:keys [name permissions id]}]
  (let [ch (chan)]
    (.put github (str "/teams/" id "/repos/" org "/" repo-name)
          #js {:permissions permissions}
          (partial gh-callback name ch))
    ch))


(defn add-teams [github org repo-name teams]
  (let [finished-ch (async/merge (map (partial add-team github org repo-name) teams))
        out-ch (chan)]
    (go-loop [result :success]
             (when-let [[err status body headers team-name] (<! finished-ch)]
               (if err
                 (do (println (str "github: error adding team " team-name ": " (format-error repo-name err)))
                     (recur :failure))
                 (do (println (str "github: team '" team-name "' added succesfully to repo '" repo-name "'"))
                     (recur :success))))
             (>! out-ch result))
    out-ch))

(defn abort [subsystem atom']
  (do (reset! atom' abort-on-error?)
      (println (str subsystem ": ABORTING"))))

(defn github-client [user pass]
  (let [gh (nodejs/require "octonode")]
    (new gh.client
         #js {:username user
              :password pass}
         #js {:username user
              :password pass})))

(defn setup [organization repo user pass teams]
  (println (str "Creating GitHub for '" repo "' in '" organization "'"))
  (let [abort? (atom false)
        github (github-client user pass)]
    (go
      (when (= (<! (create-repo github organization repo)) :failure)
        (abort "github.repo" abort?))
      (when-not @abort?
        (when (= (<! (add-teams github organization repo teams)) :failure)
          (abort "github.teams" abort?)))

      (<! (async/timeout 1000))

      (if @abort?
        (println "github: FAILURE")
        (println "github: SUCCESS")))))

