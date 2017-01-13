(ns repomaker.github
  (:require [repomaker.promises :refer [put&close]]
            [cljs.core.async :as async]
            [cljs.core.async :as async :refer [<! >! close! chan]]
            [cljs.nodejs :as nodejs]
            [repomaker.config :as config]
            [clojure.set :as set])
  (:use-macros [cljs.core.async.macros :only [go go-loop]]))

(defn first-error-msg [data]
  (:message (first (:errors data))))

(defn- repo-already-exists? [resp]
  (let [data (:data resp)]
    (and (= (:message data) "Validation Failed")
         (= (first-error-msg data) "name already exists on this account"))))

(defn resp-status [js-resp]
  (:status js-resp))

(defn http-success? [resp]
  (< 199
     (:status resp)
     300))

(defn format-error [resp]
  (if-let [msg (:message (:data resp))]
    (str "[" (:status resp) "='" (:statusText resp) "'] "
         msg)
    resp))

(def supported-methods #{:get :post :put})
(defn request [make-gh-client method path obj & {:keys [context]}]
  (when-not (supported-methods method)
    (throw (new js/Error (str "Unsupported method: " method))))
  (let [c (chan)]
    (-> (.request (make-gh-client)
                  #js {:method (name method)
                       :url    path
                       :data   (clj->js obj)})
        (.then (put&close c (or context "github.generic"))
               (put&close c (or context "github.generic"))))
    c))

(defn create-repo [gh-http org repo private?]
  (let [ch (chan)]
    (go
      (let [[js-resp _] (<! (request gh-http
                                     :post (str "/orgs/" org "/repos")
                                     {:name repo :private private?}))
            resp (js->clj js-resp :keywordize-keys true)
            log (partial println "github.create-repo:")]
        (cond
          (repo-already-exists? resp)
          (do
            (>! ch gh-http)
            (log (str "repo '" repo "' already exists")))

          (not (http-success? resp))
          (do
            (close! ch)
            (log (str "error creating repo '" repo "': " (format-error resp))))

          :else (do (>! ch gh-http)
                    (log "github: repo succesfully created")))))
    ch))



(defn add-team [gh-http org repo-name {:keys [name permissions id]}]
  (request gh-http
           :put (str "/teams/" id "/repos/" org "/" repo-name)
           {:permission permissions}
           :context name))


(defn add-teams [gh-http org repo-name teams]
  (let [finished-ch (async/merge (map
                                   (partial add-team gh-http org repo-name)
                                   teams))
        out-ch (chan)
        log (partial println "github.add-teams:")]
    (go-loop [result :success]
      (when-let [[js-resp team-name] (<! finished-ch)]
        (let [resp (js->clj js-resp :keywordize-keys true)]
          (if (not (http-success? resp))
            (do (log (str "error adding team '" team-name
                          "' to repo '" repo-name "': " (format-error resp)))
                (recur :failure))
            (do (log (str "team '" team-name "' added succesfully to repo '" repo-name "'"))
                (recur :success)))))
      (if (= result :success)
        (>! out-ch gh-http)
        (close! out-ch)))
    out-ch))

(defn github-client [user pass]
  (.create (cljs.nodejs/require "axios")
           #js {:baseURL        "https://api.github.com/"
                :timeout        1000
                :auth           #js {:username user
                                     :password pass}
                :validateStatus nil}))

(defn permissions-for [teams team-name]
  (->> teams
       (filter #(= (:name %) team-name))
       (first)
       (:permissions)))

(defn teams-with-id [gh-http org teams]
  (let [out-ch (chan)
        team-names (set (map :name teams))
        log (partial println "github.fetch-teams:")]
    (go
      (let [[js-resp _] (<! (request gh-http
                                     :get (str "/orgs/" org "/teams?per_page=100")
                                     {}))
            resp (js->clj js-resp :keywordize-keys true)]
        (if (http-success? resp)
          (>! out-ch (->> (:data resp)
                          (filter #(contains? team-names (:name %)))
                          (map (fn [{:keys [name id]}]
                                 {:name        name
                                  :id          id
                                  :permissions (permissions-for teams name)}))))
          (do (log (str "Unable to fetch teams: " resp))
              (close! out-ch)))))
    out-ch))

(defn name-set [teams]
  (->> teams
       (map #(:name %))
       (set)))

(defn all-teams-found? [fetched-teams actual-teams]
  (let [name-diff (set/difference (name-set actual-teams) (name-set fetched-teams))
        log (partial println "github.fetch-teams:")]
    (if (empty? name-diff)
      fetched-teams
      (log (str "Unable to fetch following team ids: " name-diff)))))

(defn setup [organization repo user pass teams private?]
  (println (str "Creating GitHub for '" repo "' in '" organization "'"))
  (let [gh-http #(.create (cljs.nodejs/require "axios")
                          #js {:baseURL        "https://api.github.com/"
                               :timeout        1000
                               :auth           #js {:username user
                                                    :password pass}
                               :validateStatus nil})]
    (go (-> (some->
              (create-repo gh-http organization repo private?)
              (<!)
              (teams-with-id organization teams)
              (<!)
              (all-teams-found? teams)
              (->> (add-teams gh-http organization repo))
              (<!))
            (if
              (println "github: SUCESS")
              (println "github: FAILURE"))))))

