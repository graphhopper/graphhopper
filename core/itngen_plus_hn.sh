STARTTIME=$(date +%s)

LOGIT=
#LOGIT=:${HOME}/.m2/repository/org/slf4j/slf4j-log4j12/1.7.7/slf4j-log4j12-1.7.7.jar:${HOME}/.m2/repository/log4j/log4j/1.2.17/log4j-1.2.17.jar

# Highways Network Data
#ITNDATA=/data/Development/highways_network
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/hn-gh

HN_DATA=/data/Development/highways_network_full/
HN_GRAPH_LOCATION=${HOME}/Documents/graphhopper/core/hn-gh

# Actual ITN Data
#ITNDATA=${HOME}/Development/OSMMITN/data
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/itn-gh

# Sample Data
#ITNDATA=${HOME}/Development/geoserver-service-test/geoservertest/itn-sample-data/58096-SX9192-2c1.gz
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/58096-SX9192-2c1-gh

# Modified Sample Data
ITNDATA=/media/sf_/media/shared/modified-exeter/58096-SX9192-modified.xml
GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/58096-SX9192-modified-gh

#ITNDATA=${HOME}/Development/graphhopper2/graphhopper/tools/os-itn-m27-m3-north.xml
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/os-itn-m27-m3-north-gh

#ITNDATA=${HOME}/Development/graphhopper2/graphhopper/core/os-itn-carlisle-warwick-road.xml
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/os-itn-carlisle-warwick-road-gh

#ITNDATA=${HOME}/Development/graphhopper2/graphhopper/tools/os-itn-wickham-direction-error.xml
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/os-itn-wickham-direction-error-gh

echo "Remove " ${GRAPHOUTPUTDIR} 
rm -rf ${GRAPHOUTPUTDIR} 
rm -rf ${HN_GRAPH_LOCATION} 

java -Xmx4596m -Xms2048m -XX:+UseParallelGC -XX:+UseParallelOldGC -cp ../tools/target/classes:target/classes:${HOME}/.m2/repository/net/java/dev/jsr-275/jsr-275/1.0-beta-2/jsr-275-1.0-beta-2.jar:${HOME}/.m2/repository/java3d/vecmath/1.3.2/vecmath-1.3.2.jar:${HOME}/.m2/repository/org/geotools/gt-opengis/12.1/gt-opengis-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-epsg-hsql/12.1/gt-epsg-hsql-12.1.jar:${HOME}/.m2/repository/org/hsqldb/hsqldb/2.3.2/hsqldb-2.3.2.jar:${HOME}/.m2/repository/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar:${HOME}/.m2/repository/org/geotools/gt-referencing/12.1/gt-referencing-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-metadata/12.1/gt-metadata-12.1.jar:${HOME}/.m2/repository/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar:${HOME}/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar${LOGIT} com.graphhopper.tools.Import osmreader.osm=${ITNDATA} reader.implementation=OSITN graph.location=${GRAPHOUTPUTDIR} hn.data=${HN_DATA} hn.graph.location=${HN_GRAPH_LOCATION} graph.flagEncoders="car|turnCosts=true" prepare.chWeighting=none osmreader.acceptWay=car config=../config.properties

ENDTIME=$(date +%s)
echo "Generation took $[$ENDTIME - $STARTTIME] seconds"
