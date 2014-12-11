LOGIT=
#LOGIT=:${HOME}/.m2/repository/org/slf4j/slf4j-log4j12/1.7.7/slf4j-log4j12-1.7.7.jar:${HOME}/.m2/repository/log4j/log4j/1.2.17/log4j-1.2.17.jar

# Actual ITN Data
ITNDATA=${HOME}/Development/OSMMITN/data
GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/osmitn-gh

# Sample Data
#ITNDATA=${HOME}/Development/geoserver-service-test/geoservertest/itn-sample-data/58096-SX9192-2c1.gz
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/58096-SX9192-2c1-gh

#ITNDATA=${HOME}/Development/graphhopper2/graphhopper/core/os-itn-lake-road-spicer-street.xml
#GRAPHOUTPUTDIR=${HOME}/Documents/graphhopper/core/os-itn-lake-road-spicer-street-gh

java -Xmx4596m -Xms2048m -XX:+UseParallelGC -XX:+UseParallelOldGC -cp ../tools/target/classes:target/classes:${HOME}/.m2/repository/net/java/dev/jsr-275/jsr-275/1.0-beta-2/jsr-275-1.0-beta-2.jar:${HOME}/.m2/repository/java3d/vecmath/1.3.2/vecmath-1.3.2.jar:${HOME}/.m2/repository/org/geotools/gt-opengis/12.1/gt-opengis-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-epsg-hsql/12.1/gt-epsg-hsql-12.1.jar:${HOME}/.m2/repository/org/hsqldb/hsqldb/2.3.2/hsqldb-2.3.2.jar:${HOME}/.m2/repository/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar:${HOME}/.m2/repository/org/geotools/gt-referencing/12.1/gt-referencing-12.1.jar:${HOME}/.m2/repository/org/geotools/gt-metadata/12.1/gt-metadata-12.1.jar:${HOME}/.m2/repository/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar:${HOME}/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar${LOGIT} com.graphhopper.tools.Import osmreader.osm=${ITNDATA} reader.implementation=OSITN graph.location=${GRAPHOUTPUTDIR} config=../config.properties


