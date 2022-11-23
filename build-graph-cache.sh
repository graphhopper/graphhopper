#!/bin/sh
set -e
set -u
echo 'Fetching osm.pbf...';
curl -J "https://download.geofabrik.de$GEOFABRIK_PATH" -o $OSM_PBF_FILE_NAME --silent;
echo 'Finished downloading osm.pbf.';

echo 'Fetching gtfs...';
curl -J "https://api.511.org$API_511_PATH&api_key=$API_511_TOKEN" -o $API_511_FILE_NAME --silent;
echo 'Finished downloading gtfs.';

echo 'Building graph-cache...';
java -jar *.jar import /graphhopper/bay-area/config.yml;
echo 'Finished graph-cache.';

echo 'Building graph-cache tarball...';
tar -zvcf "graph-cache.tar.gz" ./graph-cache;
echo 'Finished building tarball.';

echo 'Uploading tarball to MINIO...';
AWS_ACCESS_KEY_ID=$ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SECRET_KEY aws s3api put-object --endpoint-url $MINIO_HOST --bucket $BUCKET_NAME --key graphhopper/graph-cache.tar.gz --body graph-cache.tar.gz;
echo 'Finished uploading tarball to MINIO.';
