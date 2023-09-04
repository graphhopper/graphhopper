#!/bin/sh
set -e
set -u

echo 'Building graph-cache...';
exec java $JVM_ARGS -jar *.jar import /graphhopper/bay-area/config.yml;
echo 'Finished graph-cache.';
