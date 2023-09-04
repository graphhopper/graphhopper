#!/bin/sh
set -e

if [ -z "$(ls -A ./graph-cache)" ]; then # if empty graphhopper dir
  echo "Downloading graphhopper tarball..."
  AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api get-object --endpoint-url $MINIO_HOST --bucket $BUCKET_NAME --key $GRAPH_CACHE_KEY $OSM_PBF_FILE_NAME
  echo "Downloaded graphhopper tarball."
  echo "Decompressing graphhopper tarball..."
  tar --use-compress-program="pigz -d" -xf $OSM_PBF_FILE_NAME
  echo "Decompressed graphhopper tarball."
fi

echo "Starting graphhopper..."

exec java $JVM_ARGS -Ddw.graphhopper.graph.location=./graph-cache -jar *.jar server /graphhopper/bay-area/config.yml
