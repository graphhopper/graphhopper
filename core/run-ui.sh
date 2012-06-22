#!/bin/bash

SMALL=$1
if [ "x$SMALL" = "x" ]; then
 OSM=unterfranken.osm
 LINK="http://download.geofabrik.de/osm/europe/germany/bayern/unterfranken.osm.bz2"
 TARGET=./target
 JAVA_OPTS_IMPORT="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx300m -Xms300m"
 JAVA_OPTS=$JAVA_OPTS_IMPORT
 SIZE=5000000
else
 # This was used for Germany:
 OSM=germany.osm
 LINK=http://download.geofabrik.de/osm/europe/germany.osm.bz2

 # For import we need a lot more memory but for executing we need to reduce it
 # in order to use off-heap memory
 JAVA_OPTS_IMPORT="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx2700m -Xms2700m"
 JAVA_OPTS="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx700m -Xms700m"
 SIZE=35000000
fi

DEBUG=false
DIJKSTRA=false
GRAPH=graph-$OSM
JAR=target/graphhopper-1.0-SNAPSHOT-jar-with-dependencies.jar

if [ ! -f "$OSM" ]; then
  echo "WARNING: Running this will use ~500MB on disc: 450MB for the OSM file (only 40MB for the download of the compressed one) and 30MB for the graph"
  read -e  
  BZ=$OSM.bz2
  rm $BZ
  echo "## downloading OSM file from $LINK"
  wget -O $BZ $LINK
  echo "## extracting $BZ"
  bzip2 -d $BZ
else
  echo "## using existing osm file $OSM"
fi

if [ ! -d "$TARGET" ]; then
  echo "## build graphhopper"
  #mvn clean
  mvn -DskipTests=true assembly:assembly > /dev/null
else
  echo "## existing graphhopper found"
fi

if [ ! -d "$GRAPH" ]; then
  echo "## create graph $GRAPH (folder) from $OSM (file)"
  echo "## HINT: put the osm on an external usb drive which should speed up import time"
  java $JAVA_OPTS_IMPORT -cp $JAR de.jetsli.graph.reader.OSMReader graph=$GRAPH osm=$OSM size=$SIZE dijkstra=$DIJKSTRA
else
  echo "## using existing graph at $GRAPH"
fi

if [ -d "$GRAPH" ]; then
  java $JAVA_OPTS -cp $JAR de.jetsli.graph.ui.MiniGraphUI graph=$GRAPH debug=$DEBUG
else
  echo "## creating graph failed"
fi