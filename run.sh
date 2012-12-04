#!/bin/bash
JAVA=$JAVA_HOME/bin/java
if [ "x$JAVA_HOME" = "x" ]; then
 JAVA=java
fi

vers=`$JAVA -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \"`
echo "using java $vers from $JAVA_HOME"

FILE=$1
ALGO=$2
CLASS=$3

if [ "x$ALGO" = "xui" ]; then
 CLASS=com.graphhopper.ui.MiniGraphUI
else
 CLASS=com.graphhopper.reader.OSMReader
fi

if [ "x$ALGO" = "x" ]; then
 ALGO=astar
fi

if [ "x$FILE" = "x" ]; then
 FILE=unterfranken.osm
fi

# file without extension if any
NAME="${FILE%.*}"
OSM=$NAME.osm

GRAPH=$NAME-gh
VERSION=`grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1`
JAR=target/graphhopper-$VERSION-jar-with-dependencies.jar

# file without path
TMP=$(basename "$FILE")
TMP="${TMP%.*}"
#echo $TMP - $FILE - $NAME
if [ "$TMP" = "unterfranken" ]; then
 LINK="http://download.geofabrik.de/openstreetmap/europe/germany/bayern/unterfranken.osm.bz2"
 JAVA_OPTS_IMPORT="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx2000m -Xms2000m"
 JAVA_OPTS=$JAVA_OPTS_IMPORT
 SIZE=3000000
elif [ "$TMP" = "germany" ]; then
 LINK=http://download.geofabrik.de/openstreetmap/europe/germany.osm.bz2

 # For import we need a lot more memory. For the mmap storage you need to lower this in order to use off-heap memory.
 JAVA_OPTS_IMPORT="-XX:PermSize=10m -XX:MaxPermSize=10m -Xmx2200m -Xms2200m"
 JAVA_OPTS="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx1900m -Xms1900m"
 SIZE=35000000
elif [ -f $OSM ]; then
 LINK=""
 JAVA_OPTS_IMPORT="-XX:PermSize=20m -XX:MaxPermSize=20m -Xmx1000m -Xms1000m"
 JAVA_OPTS=$JAVA_OPTS_IMPORT
 SIZE=10000000
else
 echo "Sorry, your osm file $OSM was not found ... exiting"
 exit   
fi

if [ ! -f "config.properties" ]; then
  cp config-example.properties config.properties
fi

if [ ! -f "$OSM" ]; then
  echo "No OSM file found or specified. Press ENTER to grab one from internet."
  echo "Press CTRL+C if you do not have enough disc space or you don't want to download several MB."
  read -e  
  BZ=$OSM.bz2
  rm $BZ &> /dev/null
  echo "## now downloading OSM file from $LINK"

  # curl or wget
  DOWNLOADER=`which curl` 
  if [ "x$DOWNLOADER" = "x" ]; then    
    wget -O $BZ $LINK
  else
    curl -O $LINK
  fi    
  echo "## extracting $BZ"
  bzip2 -d $BZ

  if [ ! -f "$OSM" ]; then
    echo "ERROR couldn't download or extract OSM file $OSM ... exiting"
    exit
  fi
else
  echo "## using existing osm file $OSM"
fi

if [ ! -f "$JAR" ]; then
  echo "## now building graphhopper jar: $JAR"
  #mvn clean
  mvn -DskipTests=true install assembly:single > /dev/null
  returncode=$?
  if [[ $returncode != 0 ]] ; then
      echo "## compilation failed"
      exit $returncode
  fi      
else
  echo "## existing jar found $JAR"
fi

echo "## now running $CLASS. JAVA_OPTS=$JAVA_OPTS"
$JAVA $JAVA_OPTS -cp $JAR $CLASS config=config.properties osmreader.graph-location=$GRAPH osmreader.osm=$OSM osmreader.size=$SIZE osmreader.algo=$ALGO
