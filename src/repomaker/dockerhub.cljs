(ns repomaker.dockerhub
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! close! chan]]
            [cljs.pprint :as pp]
            [cljs-callback-heaven.core :refer [<print >?]])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

(def dh (let [inst (nodejs/require "docker-hub-api")]
          (.setCacheOptions inst #js {:enabled false})
          inst))

(defn delete-repo [org name]
  (.makeDeleteRequest
    dh (str "/repositories/" org "/" name)))

(defn put&close [ch context]
  (fn [data] (go (>! ch [data context])
                 (close! ch))))

(defn dh-post
  ([context path body]
   (dh-post context path body ""))
  ([context path body extract]
   (let [ch (chan)]
     (-> (.makePostRequest dh path body extract)
         (.then (put&close ch context))
         (.catch (put&close ch context)))
     ch)))

(defn dh-get [path extract]
  (let [ch (chan)]
    (-> (.makeGetRequest dh path extract)
        (.then (>? ch)))
    ch))


(defn error-text [err]
  (first (:__all__ (js->clj err :keywordize-keys true))))

(defn already-exists? [err]
  (= (error-text err) "Repository with this Name and Namespace already exists."))

(defn create-repo [org repo]
  (let [out (chan)]
    (go
      (let [[ret _] (<! (dh-post repo "/repositories/"
                                 #js {:namespace        org
                                      :name             repo
                                      :description      ""
                                      :full-description ""
                                      :is_private       true}))]
        (cond
          (already-exists? ret) (do (>! out :already-exists)
                                    (println (str "dockerhub: repo '" repo "' already exists")))
          (error-text ret) (do (>! out :failure)
                               (println (str "dockerhub: failed to create repo: " (error-text ret))))
          :else (do (>! out :success)
                    (println (str "dockerhub: successfully created repo '" repo "'")))))
      (close! out))
    out))

(def abort-on-error? true)

(defn abort [subsystem atom']
  (do (reset! atom' abort-on-error?)
      (println (str subsystem ": ABORTING"))))


(defn add-team
  [org repo-name {:keys [name id permissions]}]
  (dh-post name
           (str "repositories/" org "/" repo-name "/groups/")
           #js {:group_id id :permission permissions}))

(defn add-teams [org repo-name teams]
  (let [out-ch (chan)
        finished-ch (->> teams
                         (map (partial add-team org repo-name))
                         (async/merge))]
    (go-loop [result :success]
      (when-let [[result team-name] (<! finished-ch)]
        (if (instance? js/Error result)
          (do (println (str "dockerhub: error adding team " team-name ":" (aget result "message")))
              (recur :failure))
          (do (println (str "dockerhub: team '" team-name "' added succesfully to repo '" repo-name "'"))
              (recur :success))))
      (>! out-ch result))
    out-ch))

(defn login [user pass failed?]
  (let [ch (chan)]
    (-> (.login dh user pass)
        (.then (fn [& args]
                 (println "dockerhub.login: success")
                 (go (>! ch :success))))
        (.catch (fn [& args]
                  (abort "dockerhub.login" failed?)
                  (go (>! ch :failure)))))
    ch))

(defn setup [organization name user pass teams]
  (println (str "Creating DockerHub for '" name "' in '" organization "'"))
  (println "dockerhub: logging in")
  (let [failed? (atom false)]
    (go
      (<! (login user pass failed?))
      (when-not @failed?
        (when (= (<! (create-repo organization name)) :failure)
          (abort "dockerhub.repo" failed?)))
      (when-not @failed?
        (when (= (<! (add-teams organization name teams)) :failure)
          (abort "dockerhub.teams" failed?)))

      (<! (async/timeout 1000))

      (if @failed?
        (println "dockerhub: FAILURE")
        (println "dockerhub: SUCCESS")))))

