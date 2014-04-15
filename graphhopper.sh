#!/bin/bash

GH_CLASS=com.graphhopper.GraphHopper
GH_HOME=$(dirname "$0")
JAVA=$JAVA_HOME/bin/java
if [ "x$JAVA_HOME" = "x" ]; then
 JAVA=java
fi

vers=$($JAVA -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \")
bit64=$($JAVA -version 2>&1 | grep "64-Bit")
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

USAGE="./graphhopper.sh import|ui|test|measurement|miniui|extract|build <your-osm-file>"
if [ "x$ACTION" = "x" ]; then
 echo -e "## action $ACTION not found. try \n$USAGE"
fi

function ensureOsmXml { 
  if [ "x$OSM_FILE" = "x" ]; then
    # skip
    return
  elif [ ! -s "$OSM_FILE" ]; then
    echo "File not found '$OSM_FILE'. Press ENTER to get it from: $LINK"
    echo "Press CTRL+C if you do not have enough disc space or you don't want to download several MB."
    read -e
      
    echo "## now downloading OSM file from $LINK and extracting to $OSM_FILE"
    
    if [ ${OSM_FILE: -4} == ".pbf" ]; then
       wget -S -nv -O "$OSM_FILE" "$LINK"
    elif [ ${OSM_FILE: -4} == ".ghz" ]; then
       wget -S -nv -O "$OSM_FILE" "$LINK"
       unzip "$FILE" -d "$NAME-gh"
    else    
       # make sure aborting download does not result in loading corrupt osm file
       TMP_OSM=temp.osm
       wget -S -nv -O - "$LINK" | bzip2 -d > $TMP_OSM
       mv $TMP_OSM "$OSM_FILE"
    fi
  
    if [[ ! -s "$OSM_FILE" ]]; then
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
    MAVEN_HOME=$(mvn -v | grep "Maven home" | cut -d' ' -f3)
    if [ "x$MAVEN_HOME" = "x" ]; then
      # try to detect previous downloaded version
      MAVEN_HOME="$GH_HOME/maven"
      if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
        echo "No Maven found in the PATH. Now downloading+installing it to $MAVEN_HOME"
        cd "$GH_HOME"
        MVN_PACKAGE=apache-maven-3.2.1
        wget -O maven.zip http://www.eu.apache.org/dist/maven/maven-3/3.2.1/binaries/$MVN_PACKAGE-bin.zip
        unzip maven.zip
        mv $MVN_PACKAGE maven
        rm maven.zip
      fi
    fi
  fi
}

function packageCoreJar {
  if [ ! -d "./target" ]; then
    echo "## building parent"
    "$MAVEN_HOME/bin/mvn" --non-recursive install > /tmp/graphhopper-compile.log
     returncode=$?
     if [[ $returncode != 0 ]] ; then
       echo "## compilation of parent failed"
       cat /tmp/graphhopper-compile.log
       exit $returncode
     fi                                     
  fi
  
  if [ ! -f "$JAR" ]; then
    echo "## now building graphhopper jar: $JAR"
    echo "## using maven at $MAVEN_HOME"
    #mvn clean
    "$MAVEN_HOME/bin/mvn" --projects core -DskipTests=true install assembly:single > /tmp/graphhopper-compile.log
    returncode=$?
    if [[ $returncode != 0 ]] ; then
        echo "## compilation of core failed"
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
 rm -rf ./*/target
 exit

elif [ "x$ACTION" = "xeclipse" ]; then
 prepareEclipse
 exit

elif [ "x$ACTION" = "xbuild" ]; then
 prepareEclipse
 exit  
 
elif [ "x$ACTION" = "xextract" ]; then
 echo use "./graphhopper.sh extract \"left,bottom,right,top\""
 URL="http://overpass-api.de/api/map?bbox=$2"
 #echo "$URL"
 wget -O extract.osm "$URL"
 exit
 
elif [ "x$ACTION" = "xandroid" ]; then
 prepareEclipse
 "$MAVEN_HOME/bin/mvn" --projects android install android:deploy android:run
 exit
fi

if [ "x$FILE" = "x" ]; then
  echo -e "no file specified? try \n$USAGE"
  exit
fi

# NAME = file without extension if any
NAME="${FILE%.*}"

if [ "x$FILE" == "x-" ]; then
   OSM_FILE=
elif [ ${FILE: -4} == ".osm" ]; then
   OSM_FILE="$FILE"
elif [ ${FILE: -4} == ".pbf" ]; then
   OSM_FILE="$FILE"
elif [ ${FILE: -7} == ".osm.gz" ]; then
   OSM_FILE="$FILE"
elif [ ${FILE: -3} == "-gh" ]; then
   OSM_FILE="$FILE"
   NAME=${FILE%%???}
elif [ ${FILE: -4} == ".ghz" ]; then
   OSM_FILE="$FILE"
   if [[ ! -d "$NAME-gh" ]]; then
      unzip "$FILE" -d "$NAME-gh"
   fi
else
   # no known end -> no import
   OSM_FILE=
fi

GRAPH=$NAME-gh
VERSION=$(grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1)
JAR=core/target/graphhopper-$VERSION-jar-with-dependencies.jar

LINK=$(echo $NAME | tr '_' '/')
if [ "x$FILE" == "x-" ]; then
   LINK=
elif [ ${FILE: -4} == ".osm" ]; then 
   LINK="http://download.geofabrik.de/$LINK-latest.osm.bz2"
elif [ ${FILE: -4} == ".ghz" ]; then
   LINK="http://graphhopper.com/public/maps/0.1/$FILE"      
elif [ ${FILE: -4} == ".pbf" ]; then
   LINK="http://download.geofabrik.de/$LINK-latest.osm.pbf"
else
   # e.g. if directory ends on '-gh'
   LINK="http://download.geofabrik.de/$LINK-latest.osm.pbf"
fi

if [ "x$JAVA_OPTS" = "x" ]; then
  JAVA_OPTS="-XX:PermSize=60m -XX:MaxPermSize=60m -Xmx1000m -Xms1000m -server"
fi


ensureOsmXml
ensureMaven
packageCoreJar

echo "## now $ACTION. JAVA_OPTS=$JAVA_OPTS"

if [ "x$ACTION" = "xui" ] || [ "x$ACTION" = "xweb" ]; then
  export MAVEN_OPTS="$MAVEN_OPTS $JAVA_OPTS"
  if [ "x$JETTY_PORT" = "x" ]; then  
    JETTY_PORT=8989
  fi
  WEB_JAR="$GH_HOME/web/target/graphhopper-web-$VERSION-with-dep.jar"
  if [ ! -s "$WEB_JAR" ]; then         
    "$MAVEN_HOME/bin/mvn" --projects web -DskipTests=true install assembly:single > /tmp/graphhopper-web-compile.log
    returncode=$?
    if [[ $returncode != 0 ]] ; then
      echo "## compilation of web failed"
      cat /tmp/graphhopper-web-compile.log
      exit $returncode
    fi
  fi

  RC_BASE=./web/src/main/webapp

  if [ "x$GH_FOREGROUND" = "x" ]; then
    exec "$JAVA" $JAVA_OPTS -jar "$WEB_JAR" jetty.resourcebase=$RC_BASE jetty.port=$JETTY_PORT config=$CONFIG \
         $GH_WEB_OPTS graph.location="$GRAPH" osmreader.osm="$OSM_FILE"
    # foreground => we never reach this here
  else
    exec "$JAVA" $JAVA_OPTS -jar "$WEB_JAR" jetty.resourcebase=$RC_BASE jetty.port=$JETTY_PORT config=$CONFIG \
         $GH_WEB_OPTS graph.location="$GRAPH" osmreader.osm="$OSM_FILE" <&- &
    if [ "x$GH_PID_FILE" != "x" ]; then
       echo $! > $GH_PID_FILE
    fi
    exit $?                    
  fi

elif [ "x$ACTION" = "ximport" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" $GH_CLASS printVersion=true \
      config=$CONFIG \
      $GH_IMPORT_OPTS graph.location="$GRAPH" osmreader.osm="$OSM_FILE"


elif [ "x$ACTION" = "xtest" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" $GH_CLASS printVersion=true config=$CONFIG \
       graph.location="$GRAPH" osmreader.osm="$OSM_FILE" prepare.chShortcuts=false \
       graph.testIT=true


elif [ "x$ACTION" = "xtorture" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.util.QueryTorture $3 $4 $5 $6 $7 $8 $9


elif [ "x$ACTION" = "xminiui" ]; then
 "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/tools/pom.xml" -DskipTests clean install assembly:single
 JAR=tools/target/graphhopper-tools-$VERSION-jar-with-dependencies.jar   
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.ui.MiniGraphUI osmreader.osm="$OSM_FILE" printVersion=true config=$CONFIG \
              graph.location="$GRAPH"


elif [ "x$ACTION" = "xmeasurement" ]; then
 ARGS="config=$CONFIG graph.location=$GRAPH osmreader.osm=$OSM_FILE prepare.chShortcuts=fastest osmreader.acceptWay=CAR"
 # graph.doSort=true"
 echo -e "\ncreate graph via $ARGS, $JAR"
 START=$(date +%s)
 "$JAVA" $JAVA_OPTS -cp "$JAR" $GH_CLASS $ARGS prepare.doPrepare=false
 END=$(date +%s)
 IMPORT_TIME=$(($END - $START))

 function startMeasurement {
    COUNT=5000
    commit_info=$(git log -n 1 --pretty=oneline)
    echo -e "\nperform measurement via jar=> $JAR and ARGS=> $ARGS"
    "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.util.Measurement $ARGS measurement.count=$COUNT measurement.location="$M_FILE_NAME" \
            graph.importTime=$IMPORT_TIME measurement.gitinfo="$commit_info"
 }
 
 
 # use all <last_commits> versions starting from HEAD
 last_commits=$3
  
 if [ "x$last_commits" = "x" ]; then
   # use current version
   "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/core/pom.xml" -DskipTests clean install assembly:single
   startMeasurement
   exit
 fi

 current_commit=$(git log -n 1 --pretty=oneline | cut -d' ' -f1)
 commits=$(git rev-list HEAD -n $last_commits)
 for commit in $commits; do
   git checkout $commit -q
   M_FILE_NAME=$(git log -n 1 --pretty=oneline | grep -o "\ .*" |  tr " ,;" "_")
   M_FILE_NAME="measurement$M_FILE_NAME.properties"
   echo -e "\nusing commit $commit and $M_FILE_NAME"
   
   "$MAVEN_HOME/bin/mvn" -f "$GH_HOME/core/pom.xml" -DskipTests clean install assembly:single
   startMeasurement
   echo -e "\nmeasurement.commit=$commit\n" >> "$M_FILE_NAME"
 done
 # revert checkout
 git checkout $current_commit
fi
