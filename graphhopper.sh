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

CONFIG=config.properties
if [ ! -f "config.properties" ]; then
  cp config-example.properties $CONFIG
fi

ACTION=$1
FILE=$2

USAGE="./graphhopper.sh import|ui|test <your-osm-file>"
if [ "x$ACTION" = "x" ]; then
 echo -e "## action $ACTION not found. try \n$USAGE"
fi

function ensureOsmXml { 
  if [ ! -s "$OSM_FILE" ]; then
    echo "File not found '$OSM_FILE'. Press ENTER to get it from: $LINK"
    echo "Press CTRL+C if you do not have enough disc space or you don't want to download several MB."
    read -e
      
    echo "## now downloading OSM file from $LINK and extracting to $OSM_FILE"
    
    if [ ${OSM_FILE: -4} == ".pbf" ]; then
       wget -O $OSM_FILE $LINK
    else    
       # make sure aborting download does not result in loading corrupt osm file
       TMP_OSM=temp.osm
       wget -O - $LINK | bzip2 -d > $TMP_OSM
       mv $TMP_OSM $OSM_FILE
    fi
  
    if [ ! -f "$OSM_FILE" ]; then
      echo "ERROR couldn't download or extract OSM file $OSM_FILE ... exiting"
      exit
    fi
  else
    echo "## using existing osm file $OSM_FILE"
  fi
}

function ensureMaven {
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
}

function packageCoreJar {
  if [ ! -f "$JAR" ]; then
    echo "## now building graphhopper jar: $JAR"
    echo "## using maven at $MAVEN_HOME"
    #mvn clean
    "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/core/pom.xml" -DskipTests=true install assembly:single > /tmp/graphhopper-compile.log
    returncode=$?
    if [[ $returncode != 0 ]] ; then
        echo "## compilation failed"
        cat /tmp/graphhopper-compile.log
        exit $returncode
    fi      
  else
    echo "## existing jar found $JAR"
  fi
}

function prepareEclipse {
 ensureMaven   
 packageCoreJar
 cp core/target/graphhopper-*-android.jar android/libs/   
}


## now handle actions which do not take an OSM file
if [ "x$ACTION" = "xclean" ]; then
 rm -rf */target
 exit

elif [ "x$ACTION" = "xeclipse" ]; then
 prepareEclipse
 exit
 
elif [ "x$ACTION" = "xandroid" ]; then
 prepareEclipse
 "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/android/pom.xml" install android:deploy android:run
 exit
fi

if [ "x$FILE" = "x" ]; then
  echo -e "no file specified? try \n$USAGE"
  exit
fi

# NAME = file without extension if any
NAME="${FILE%.*}"

if [ ${FILE: -4} == ".osm" ]; then
   OSM_FILE=$FILE
elif [ ${FILE: -4} == ".pbf" ]; then
   OSM_FILE=$FILE
elif [ ${FILE: -7} == ".osm.gz" ]; then
   OSM_FILE=$FILE
else
   # no end default to osm
   OSM_FILE=$NAME.osm
fi

GRAPH=$NAME-gh
VERSION=`grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1`
JAR=core/target/graphhopper-$VERSION-jar-with-dependencies.jar

# file without path (.osm.gz or osm.bz2 is also possible)
TMP=$(basename "$FILE")
TMP="${TMP%.*}"
TMP="${TMP%.*}"


if [ "x$TMP" = "xunterfranken" ]; then
 LINK="http://download.geofabrik.de/openstreetmap/europe/germany/bayern/unterfranken.osm.bz2"
 JAVA_OPTS="-XX:PermSize=60m -XX:MaxPermSize=60m -Xmx200m -Xms200m" 
elif [ "x$TMP" = "xgermany" ]; then
 LINK=http://download.geofabrik.de/openstreetmap/europe/germany.osm.bz2

 # Info: for import we need a more memory than for just loading it
 JAVA_OPTS="-XX:PermSize=60m -XX:MaxPermSize=60m -Xmx1600m -Xms1600m" 
else 
 LINK=`echo $NAME | tr '_' '/'`
 if [ ${FILE: -4} == ".osm" ]; then 
   LINK="http://download.geofabrik.de/$LINK-latest.osm.bz2"
 else
   LINK="http://download.geofabrik.de/$LINK-latest.osm.pbf"
   OSM_FILE=$NAME.pbf
 fi
 echo $LINK
 echo $OSM_FILE
 if [ "x$JAVA_OPTS" = "x" ]; then
  JAVA_OPTS="-XX:PermSize=60m -XX:MaxPermSize=60m -Xmx1000m -Xms1000m" 
 fi
fi



ensureOsmXml
ensureMaven
packageCoreJar

echo "## now $ACTION. JAVA_OPTS=$JAVA_OPTS"

if [ "x$ACTION" = "xui" ] || [ "x$ACTION" = "xweb" ]; then
 export MAVEN_OPTS="$MAVEN_OPTS $JAVA_OPTS"
 "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/web/pom.xml" -Dgraphhopper.config=$CONFIG \
      -Dgraphhopper.osmreader.osm=$OSM_FILE -Djetty.reload=manual jetty:run


elif [ "x$ACTION" = "ximport" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.GraphHopper printVersion=true config=$CONFIG \
      graph.location="$GRAPH" \
      osmreader.osm="$OSM_FILE"


elif [ "x$ACTION" = "xtest" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.GraphHopper printVersion=true config=$CONFIG \
       graph.location="$GRAPH" osmreader.osm="$OSM_FILE" prepare.chShortcuts=false \
       graph.testIT=true

       
elif [ "x$ACTION" = "xmeasurement" ]; then
 ARGS="graph.location=$GRAPH osmreader.osm=$OSM_FILE prepare.chShortcuts=fastest osmreader.acceptWay=CAR"
 echo -e "\ncreate graph via $ARGS, $JAR"
 START=$(date +%s)
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.GraphHopper $ARGS prepare.doPrepare=false
 END=$(date +%s)
 IMPORT_TIME=$(($END - $START))

 function startMeasurement {
    COUNT=5000
    ARGS="$ARGS prepare.doPrepare=true measurement.count=$COUNT measurement.location=$M_FILE_NAME graph.importTime=$IMPORT_TIME"
    echo -e "\nperform measurement via $ARGS, $JAR"
    "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.util.Measurement $ARGS
 }
 
 # use all <last_commits> versions starting from HEAD
 last_commits=$3
 
 if [ "x$last_commits" = "x" ]; then
   # use current version
   "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/core/pom.xml" -DskipTests clean install assembly:single
   startMeasurement
   exit
 fi

 current_commit=`git log -n 1 --pretty=oneline | cut -d' ' -f1`
 commits=$(git rev-list HEAD -n $last_commits)
 for commit in $commits; do
   git checkout $commit -q
   M_FILE_NAME=`git log -n 1 --pretty=oneline | grep -o "\ .*" |  tr " ,;" "_"`
   M_FILE_NAME="measurement$M_FILE_NAME.properties"
   echo -e "\nusing commit $commit and $M_FILE_NAME"
   
   "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/core/pom.xml" -DskipTests clean install assembly:single
   startMeasurement
   echo -e "\nmeasurement.commit=$commit\n" >> $M_FILE_NAME
 done
 # revert checkout
 git checkout $current_commit
fi
