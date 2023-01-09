#!/bin/sh
set -e
set -u

echo 'Building graph-cache...';
java -jar *.jar import /graphhopper/bay-area/config.yml;
echo 'Finished graph-cache.';
