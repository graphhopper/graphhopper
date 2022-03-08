#!/bin/sh
echo 'Fetching osm.pbf...';
curl -J "http://proxy-cache-svc.$POD_NAMESPACE/geofabrik$GEOFABRIK_PATH" -o $OSM_PBF_FILE_NAME --silent;
echo 'Finished downloading osm.pbf.';

echo 'Fetching gtfs...';
curl -J "http://proxy-cache-svc.$POD_NAMESPACE/511$API_511_PATH" -o $API_511_FILE_NAME --silent;
echo 'Finished downloading gtfs.';

exec java -jar *.jar server -a import /graphhopper/bay-area/config.yml

tar -zvcf "photon_data.tar.gz" ./photon_data

AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api put-object --endpoint-url http://$MINIO_HOST.$POD_NAMESPACE --bucket $BUCKET_NAME --key graphhopper/graph-cache.tar.gz --body graph-cache.tar.gz
