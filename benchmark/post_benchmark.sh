#!/bin/bash
# usage:
# benchmark/post_benchmark.sh <results_dir> <user> <pwd> <url>
#
# where:
# <results_dir> = path to a folder with measurement results to be posted
# <user>:<pwd> = credentials for basic auth
# <url> = url the results are posted to

# make this script exit if a command faiils, a variable is missing etc.
set -euo pipefail

# use curl to post results (requires external variables to be set)
for f in $1*.json
  # use some options to curl to make sure we notice when error is returned
  do curl --show-error --fail -XPOST -u $2:$3 -H "Content-Type: application/json" -d @$f $4
done

