(ns repomaker.dockerhub
  (:require [repomaker.promises :refer [put&close]]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<! >! close! chan]]
            [cljs.pprint :as pp])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

(def dh (let [inst (nodejs/require "docker-hub-api")]
          (.setCacheOptions inst #js {:enabled false})
          inst))

(defn delete-repo [org name]
  (.makeDeleteRequest
    dh (str "/repositories/" org "/" name)))

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
        (.then (put&close ch "")))
    ch))


(defn error-text [err]
  (first (:__all__ (js->clj err :keywordize-keys true))))

(defn already-exists? [err]
  (= (error-text err) "Repository with this Name and Namespace already exists."))

(defn create-repo! [org repo private?]
  (let [out (chan)]
    (go
      (let [[ret _] (<! (dh-post repo "/repositories/"
                                 #js {:namespace        org
                                      :name             repo
                                      :description      ""
                                      :full-description ""
                                      :is_private       private?}))]
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

(defn add-teams! [org repo-name teams]
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

(defn permissions-for [teams name]
  (->> teams
       (filter #(= (:name %) name))
       (first)
       (:permissions)))

(defn team-ids [organization teams]
  (let [ch (chan)]
    (go
      (let [team-names (set (map :name teams))
            [all-teams-js _] (<! (dh-get (str "/orgs/" organization "/groups") "results"))
            all-teams (js->clj all-teams-js :keywordize-keys true)]
        (>! ch (->> all-teams
                    (filter #(contains? team-names (:name %)))
                    (map (fn [{:keys [name id]}]
                           {:name        name
                            :id          id
                            :permissions (permissions-for teams name)}))))))
    ch))


(defn setup [organization name user pass teams private?]
  (println (str "Creating DockerHub for '" name "' in '" organization "'"))
  (println "dockerhub: logging in")
  (let [failed? (atom false)]
    (go
      (<! (login user pass failed?))
      (when-not @failed?
        (when (= (<! (create-repo! organization name private?)) :failure)
          (abort "dockerhub.repo" failed?)))
      (when-not @failed?
        (let [teams-with-id (<! (team-ids organization teams))]
          (if (not= (count teams-with-id) (count teams))
            (abort "dockerhub.fetch-teams" failed?))
          (when (= (<! (add-teams! organization name teams-with-id)) :failure)
            (abort "dockerhub.teams" failed?))))

      (<! (async/timeout 1000))

      (if @failed?
        (println "dockerhub: FAILURE")
        (println "dockerhub: SUCCESS")))))

