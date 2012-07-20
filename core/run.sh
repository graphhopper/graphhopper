#!/bin/bash

vers=`$JAVA_HOME/bin/java -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \"`
echo "using java $vers from $JAVA_HOME"

FILE=$1
CLASS=$2
ALGO=$3

if [ "x$CLASS" = "x" ]; then
# CLASS=de.jetsli.graph.ui.MiniGraphUI
 CLASS=de.jetsli.graph.reader.OSMReader
fi

if [ "x$ALGO" = "x" ]; then
 ALGO=astar
fi

if [ "x$FILE" = "x" ]; then
 FILE=unterfranken
fi

OSM=$FILE.osm

# for perf tests
TEST=false
SPATH=true

# for UI
DEBUG=false

GRAPH=graph-$OSM
JAR=target/graphhopper-1.0-SNAPSHOT-jar-with-dependencies.jar

if [ "$FILE" = "unterfranken" ]; then
 LINK="http://download.geofabrik.de/osm/europe/germany/bayern/unterfranken.osm.bz2"
 JAVA_OPTS_IMPORT="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx300m -Xms300m"
 JAVA_OPTS=$JAVA_OPTS_IMPORT
 SIZE=5000000
elif [ "$FILE" = "germany" ]; then
 LINK=http://download.geofabrik.de/osm/europe/germany.osm.bz2

 # For import we need a lot more memory. For the mmap storage you need to lower this in order to use off-heap memory.
 JAVA_OPTS_IMPORT="-XX:PermSize=10m -XX:MaxPermSize=10m -Xmx2700m -Xms2700m"
 JAVA_OPTS="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx2700m -Xms2700m"
 SIZE=35000000
else
 echo "Sorry, your input $FILE was not found ... exiting"
 exit
fi

if [ ! -f "$OSM" ]; then
  echo "WARNING: Running this will use lots of space on disc and download several MB"
  read -e  
  BZ=$OSM.bz2
  rm $BZ
  echo "## now downloading OSM file from $LINK"
  wget -O $BZ $LINK
  echo "## extracting $BZ"
  bzip2 -d $BZ
else
  echo "## using existing osm file $OSM"
fi

if [ ! -f "$JAR" ]; then
  echo "## now building graphhopper jar: $JAR"
  #mvn clean
  mvn -DskipTests=true assembly:assembly > /dev/null
else
  echo "## existing jar found $JAR"
fi

if [ ! -d "$GRAPH" ]; then
  echo "## now creating graph $GRAPH (folder) from $OSM (file). java opts=$JAVA_OPTS_IMPORT"
  echo "## HINT: put the osm on an external usb drive which should speed up import time"
  $JAVA_HOME/bin/java $JAVA_OPTS_IMPORT -cp $JAR de.jetsli.graph.reader.OSMReader graph=$GRAPH osm=$OSM size=$SIZE
else
  echo "## using existing graph at $GRAPH"
fi

if [ -d "$GRAPH" ]; then
  echo "## now running $CLASS. java opts=$JAVA_OPTS_IMPORT"
  $JAVA_HOME/bin/java $JAVA_OPTS -cp $JAR $CLASS graph=$GRAPH debug=$DEBUG test=$TEST shortestpath=$SPATH algo=$ALGO
else
  echo "## creating graph failed"
fi
