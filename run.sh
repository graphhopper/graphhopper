#!/bin/bash

GH_HOME=$(dirname $0)
JAVA=$JAVA_HOME/bin/java
if [ "x$JAVA_HOME" = "x" ]; then
 JAVA=java
fi

vers=`$JAVA -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \"`
bit64=`$JAVA -version 2>&1 | grep "64-Bit"`
if [ "x$bit64" != "x" ]; then
  vers="$vers (64bit)"
fi
echo "## using java $vers from $JAVA_HOME"

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
OSM_XML=$NAME.osm

GRAPH=$NAME-gh
VERSION=`grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1`
JAR=target/graphhopper-$VERSION-jar-with-dependencies.jar

# file without path
TMP=$(basename "$FILE")
TMP="${TMP%.*}"
#echo $TMP - $FILE - $NAME
if [ "x$TMP" = "xunterfranken" ]; then
 LINK="http://download.geofabrik.de/openstreetmap/europe/germany/bayern/unterfranken.osm.bz2"
 JAVA_OPTS="-XX:PermSize=30m -XX:MaxPermSize=30m -Xmx100m -Xms100m"
 SIZE=3000000
elif [ "x$TMP" = "xgermany" ]; then
 LINK=http://download.geofabrik.de/openstreetmap/europe/germany.osm.bz2

 # For import we need a lot more memory. For the mmap storage you need to lower this in order to use off-heap memory.
 JAVA_OPTS="-XX:PermSize=30m -XX:MaxPermSize=30m -Xmx1600m -Xms1600m"
 SIZE=35000000
elif [ -f $OSM_XML ]; then
 LINK=""
 JAVA_OPTS="-XX:PermSize=30m -XX:MaxPermSize=30m -Xmx280m -Xms280m"
 SIZE=10000000
else
 echo "Sorry, your osm file $OSM_XML was not found ... exiting"
 exit   
fi

if [ ! -f "config.properties" ]; then
  cp config-example.properties config.properties
fi

if [ ! -f "$OSM_XML" ]; then
  echo "No OSM file found or specified. Press ENTER to grab one from internet."
  echo "Press CTRL+C if you do not have enough disc space or you don't want to download several MB."
  read -e  
  BZ=$OSM_XML.bz2
  rm $BZ &> /dev/null
  
  echo "## now downloading OSM file from $LINK"
  wget -O $BZ $LINK
  
  echo "## extracting $BZ"
  bzip2 -d $BZ

  if [ ! -f "$OSM_XML" ]; then
    echo "ERROR couldn't download or extract OSM file $OSM_XML ... exiting"
    exit
  fi
else
  echo "## using existing osm file $OSM_XML"
fi

# maven home existent?
if [ "x$MAVEN_HOME" = "x" ]; then
  # not existent but probably is maven in the path?
  MAVEN_HOME=`mvn -v | grep "Maven home" | cut -d' ' -f3`
  if [ "x$MAVEN_HOME" = "x" ]; then
    # try to detect previous downloaded version
    MAVEN_HOME="$GH_HOME/maven"
    if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
      echo "No Maven found in the PATH. Now downloading+installing it to $MAVEN_HOME"
      cd "$GH_HOME"
      MVN_PACKAGE=apache-maven-3.0.5
      wget -O maven.zip http://www.eu.apache.org/dist/maven/maven-3/3.0.5/binaries/$MVN_PACKAGE-bin.zip
      unzip maven.zip
      mv $MVN_PACKAGE maven
      rm maven.zip
    fi
  fi
fi

if [ ! -f "$JAR" ]; then
  echo "## now building graphhopper jar: $JAR"
  echo "## using maven at $MAVEN_HOME"
  #mvn clean
  $MAVEN_HOME/bin/mvn -DskipTests=true install assembly:single > /tmp/graphhopper-compile.log
  returncode=$?
  if [[ $returncode != 0 ]] ; then
      echo "## compilation failed"
      cat /tmp/graphhopper-compile.log
      exit $returncode
  fi      
else
  echo "## existing jar found $JAR"
fi

echo "## now running $CLASS. algo=$ALGO. JAVA_OPTS=$JAVA_OPTS"
"$JAVA" $JAVA_OPTS -cp "$JAR" $CLASS printVersion=true config=config.properties osmreader.graph-location="$GRAPH" osmreader.osm="$OSM_XML" osmreader.size=$SIZE osmreader.algo=$ALGO
