(ns dynamodb-backup.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [repomaker.core-test]))

(doo-tests 'repomaker.core-test)
