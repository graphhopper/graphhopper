#!/bin/sh
set -e
# AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api get-object --endpoint-url http://$MINIO_HOST.$POD_NAMESPACE --bucket $BUCKET_NAME --key graphhopper/graph-cache.tar.gz graph-cache.tar.gz
AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api get-object --endpoint-url http://$MINIO_HOST.staging --bucket $BUCKET_NAME --key graphhopper/graph-cache.tar.gz graph-cache.tar.gz

tar -xvzf graph-cache.tar.gz

exec java $JVM_ARGS -Ddw.graphhopper.graph.location=./graph-cache -jar *.jar server /graphhopper/bay-area/config.yml
