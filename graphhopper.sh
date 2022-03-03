#!/bin/sh
echo 'Fetching osm.pbf...';
curl -J "http://proxy-cache-svc.$POD_NAMESPACE/geofabrik$GEOFABRIK_PATH" -o $OSM_PBF_FILE_NAME --silent;
echo 'Finished downloading osm.pbf.';

echo 'Fetching gtfs...';
curl -J "http://proxy-cache-svc.$POD_NAMESPACE/511$API_511_PATH" -o $API_511_FILE_NAME --silent;
echo 'Finished downloading gtfs.';

exec java -jar *.jar server /graphhopper/bay-area/config.yml
