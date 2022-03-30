#!/bin/bash -e

GH_BPF_FILE=$(basename "$GH_BPF_SOURCE")

[[ -f "$GH_DATA_VOLUME/$GH_BPF_FILE" ]] || {
    echo "Downloading $GH_BPF_SOURCE..."
    curl "$GH_BPF_SOURCE" --output "$GH_DATA_VOLUME/$GH_BPF_FILE"
}

java -Ddw.graphhopper.datareader.file="$GH_DATA_VOLUME/$GH_BPF_FILE" -jar graphhopper*.jar server "$GH_CONFIG_FILE"


#docker run --entrypoint /bin/bash israelhikingmap/graphhopper -c "wget https://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf -O /data/berlin.osm.pbf && java -Ddw.graphhopper.datareader.file=/data/berlin.osm.pbf -Ddw.graphhopper.graph.location=berlin-gh -jar *.jar server config-example.yml"


# OmitStackTraceInFastThrow prevent the jvm to stop including the stacktrace in the exception and logging after the exception has been raised a few times
#CMD java -server -XX:-OmitStackTraceInFastThrow -XX:InitialRAMPercentage=60 -XX:MaxRAMPercentage=90 -jar -Dspring.profiles.active=${SQUIRREL_ENVIRONMENT} -Dfile.encoding=UTF-8 -Duser.timezone=GMT -javaagent:dd-java-agent.jar trip_feed_exporter-1.0.0.jar

