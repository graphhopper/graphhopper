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
  # we need to make sure that the error response is printed *and* the build fails if there is an error status code
  do output="$(curl -i -s --show-error -XPOST -u $2:$3 -H "Content-Type: application/json" -w '\nhttp_status=%{http_code}' -d @$f $4)"
  echo $output
  http_status="$(echo $output | sed -n 's/.*http_status=//p')"
  if [ $http_status -gt 300 ]; then
    exit 1
  fi
done

