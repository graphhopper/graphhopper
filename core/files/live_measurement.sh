#!/bin/bash

# before execution do
# 1. cp files/measurement.sh files/live_measurement.sh
#    to ensure that you have your customized measurement.sh file available and git has no problems to switch versions
# 2. adapt memory usage in JAVA_OPTS
# 3. adapt the OSM location GH_MAIN
# 4. adapt last_commits

GH_HOME=$(dirname $0)/..
cd $GH_HOME

JAVA=$JAVA_HOME/bin/java
if [ "x$JAVA_HOME" = "x" ]; then
 JAVA=java
fi

VERSION=`grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1`
JAR=target/graphhopper-$VERSION-jar-with-dependencies.jar

# make sure this stays the same for all measurements
JAVA_OPTS="-Xmx1000m -Xms1000m" 

GH_MAIN=/media/SAMSUNG/maps/unterfranken

# should we call?
# mvn clean install assembly:single

# import graph
OSM_XML=$GH_MAIN.osm
GL=$GH_MAIN-gh
ARGS="osmreader.graph-location=$GL osmreader.osm=$OSM_XML osmreader.chShortcuts=fastest osmreader.type=CAR"
echo -e "\ncreate graph via $ARGS, $JAR"
$JAVA $JAVA_OPTS -cp $JAR com.graphhopper.reader.OSMReader $ARGS osmreader.doPrepare=false

function startMeasurement {
  COUNT=5000
  ARGS="$ARGS osmreader.doPrepare=true measurement.count=$COUNT measurement.location=$M_FILE_NAME"
  echo -e "\nperform measurement via $ARGS, $JAR"
  $JAVA $JAVA_OPTS -cp $JAR com.graphhopper.util.Measurement $ARGS
}

# use current version
mvn -DskipTests clean install assembly:single  
startMeasurement
exit

# use git
last_commits=1
commits=$(git rev-list HEAD -n $last_commits)
for commit in $commits; do
  git checkout $commit -q
  M_FILE_NAME=`git log -n 1 --pretty=oneline | grep -o "\ .*" |  tr " ,;" "_"`
  M_FILE_NAME="measurement$M_FILE_NAME.properties"
  echo -e "\nusing commit $commit and $M_FILE_NAME"
  
  mvn -DskipTests clean install assembly:single
  startMeasurement
done
