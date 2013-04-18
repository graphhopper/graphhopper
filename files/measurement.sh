#!/bin/bash
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
echo $"\ncreate graph via $ARGS, $JAR"
$JAVA $JAVA_OPTS -cp $JAR com.graphhopper.reader.OSMReader $ARGS osmreader.doPrepare=false

function startMeasurement {
  COUNT=5000
  ARGS="$ARGS osmreader.doPrepare=true measurement.count=$COUNT measurement.location=$M_FILE_NAME"
  echo $"perform measurement via $ARGS, $JAR"
  $JAVA $JAVA_OPTS -cp $JAR com.graphhopper.util.Measurement $ARGS
}

last_commits=1
commits=$(git rev-list HEAD -n $last_commits)
for commit in $commits; do
  git checkout $commit -q
  M_FILE_NAME=`git log -n 1 --pretty=oneline | grep -o "\ .*" |  tr " ,;" "_"`
  M_FILE_NAME="measurement$M_FILE_NAME.properties"
  echo "using commit $commit and $M_FILE_NAME"
  
  mvn -DskipTests clean install assembly:single
  startMeasurement
done
