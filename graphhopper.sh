#!/bin/sh
echo 'Fetching osm.pbf...';
curl -J "http://graphhopper-data-server-svc.$POD_NAMESPACE.svc.cluster.local/geofabrik$GEOFABRIK_PATH" -o $OSM_PBF_FILE_NAME --silent;
echo 'Finished downloading osm.pbf.';

echo 'Fetching gtfs...';
curl -J "http://graphhopper-data-server-svc.$POD_NAMESPACE.svc.cluster.local/511$API_511_PATH" -o $API_511_FILE_NAME --silent;
echo 'Finished downloading gtfs.';

exec java -jar *.jar server /graphhopper/bay-area/config.yml
