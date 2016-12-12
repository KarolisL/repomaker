#!/bin/bash

delay=${1:-1}

while [ 1 ]; do 
    NOP=true node target/server_dev/repomaker.js --repo int-test1 
    printf "$(date)::: Sleeping for ${delay} sec\n"
    sleep ${delay}
done
