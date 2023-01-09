#!/bin/sh
set -e
AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api get-object --endpoint-url $MINIO_HOST --bucket $BUCKET_NAME --key $GRAPH_CACHE_KEY $OSM_PBF_FILE_NAME

tar --use-compress-program="pigz -d" -xzf $OSM_PBF_FILE_NAME

exec java $JVM_ARGS -Ddw.graphhopper.graph.location=./graph-cache -jar *.jar server /graphhopper/bay-area/config.yml
