# repomaker

A tool to help with creating reps in multiple providers.
Currently supported:
* github
* dockerhub

## Config
It expects two config files:
* ~/.repomaker/credentials.edn
e.g.:
```
{:type      "basic"
 :github    {:user "<username>" :pass "<password-or-api-key>"}
  :dockerhub {:user "<username>" :pass "<password>"}}
```
*  ~/.repomaker/teams.edn
```
{:<type> {:github    {:teams [{:name "<team-name>" :permissions "push" :id 1936383}]
                      :org   "<github-or>"}
          :dockerhub {:teams [{:name "<team-name>", :permissions "read", :id 42963}
                      :org   "<dockerhub-org>"}}}
```

## Why is it Clojure?
Because I wanted to code in clojure

## Why is it ClojureScript
Because startup time of JVM is huge. NodeJs starts a lot faster

