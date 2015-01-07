LOGIT=
#LOGIT=:${HOME}/.m2/repository/org/slf4j/slf4j-log4j12/1.7.7/slf4j-log4j12-1.7.7.jar:${HOME}/.m2/repository/log4j/log4j/1.2.17/log4j-1.2.17.jar

# Exeter Data
#DATA=${HOME}/Development/geoserver-service-test/geoservertest/itn-sample-data/58096-SX9192-2c1.xml
# Full UK Data
DATA=${HOME}/Development/OSMMITN/data

#NODE_LIST=4000000023101616,4000000023101627,4000000023101630,4000000023102506,4000000023102509 
NODE_LIST=4000000007082052,4000000007082053,4000000007176328,4000000007081784,4000000007082067,4000000007176334,4000000007176336,4000000007176337,4000000007180714,4000000007082054,4000000007166192,4000000007081404
#ITN_OUTPUT=os-itn-m27-m3-north.xml
ITN_OUTPUT=os-itn-carlisle-warwick-road.xml

java -Xmx4096m -Xms2048m -XX:+UseParallelGC -XX:+UseParallelOldGC -cp ../tools/target/classes:target/classes:${HOME}/.m2/repository/net/java/dev/jsr-275/jsr-275/1.0-beta-2/jsr-275-1.0-beta-2.jar:${HOME}/.m2/repository/java3d/vecmath/1.3.2/vecmath-1.3.2.jar:${HOME}/.m2/repository/org/geotools/gt-opengis/12.1/gt-opengis-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-epsg-hsql/12.1/gt-epsg-hsql-12.1.jar:${HOME}/.m2/repository/org/hsqldb/hsqldb/2.3.2/hsqldb-2.3.2.jar:${HOME}/.m2/repository/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar:${HOME}/.m2/repository/org/geotools/gt-referencing/12.1/gt-referencing-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-metadata/12.1/gt-metadata-12.1.jar:${HOME}/.m2/repository/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar:${HOME}/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar${LOGIT} com.graphhopper.tools.OsITNProblemRouteExtractor osmreader.osm=${DATA} reader.implementation=OSITN itnoutput="${ITN_OUTPUT}" nodeList="${NODE_LIST}"

