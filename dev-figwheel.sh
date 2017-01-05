#!/bin/bash

delay=${1:-1}

while [ 1 ]; do 
    node target/dev/repomaker.js --invalid-opt
    printf "$(date)::: Sleeping for ${delay} sec\n"
    sleep ${delay}
done
