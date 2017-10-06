#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required for handling Windows cr/lf 
# See StackOverflow answer http://stackoverflow.com/a/14607651

GH_CLASS=com.graphhopper.tools.Import
GH_HOME=$(dirname "$0")
JAVA=$JAVA_HOME/bin/java
if [ "$JAVA_HOME" = "" ]; then
 JAVA=java
fi

vers=$($JAVA -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \")
bit64=$($JAVA -version 2>&1 | grep "64-Bit")
if [ "$bit64" != "" ]; then
  vers="$vers (64bit)"
fi
echo "## using java $vers from $JAVA_HOME"

CONFIG=config.properties
if [ ! -f "config.properties" ]; then
  cp config-example.properties $CONFIG
fi

ACTION=$1
FILE=$2

function printUsage {
 echo
 echo "./graphhopper.sh import|web <some-map-file>"
 echo "./graphhopper.sh clean|build|buildweb|help"
 echo
 echo "  help        this message"
 echo "  import      creates the graphhopper files used for later (faster) starts"
 echo "  web         starts a local server for user access at localhost:8989 and API access at localhost:8989/route"
 echo "  build       creates the graphhopper JAR (without the web module)"
 echo "  buildweb    creates the graphhopper JAR (with the web module)"
 echo "  clean       removes all JARs, necessary if you need to use the latest source (e.g. after switching the branch etc)"
 echo "  measurement does performance analysis of the current source version via random routes (Measurement class)"
 echo "  torture     can be used to test real world routes via feeding graphhopper logs into a GraphHopper system (Torture class)"
 echo "  miniui      is a simple Java/Swing visualization application used for debugging purposes (MiniGraphUI class)"
 echo "  extract     calls the overpass API to grab any area as .osm file"
 echo "  android     builds, deploys and starts the Android demo for your connected device"
}

if [ "$ACTION" = "" ]; then
 echo "## action $ACTION not found. try" 
 printUsage
fi

function ensureOsm { 
  if [ "$OSM_FILE" = "" ]; then
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
       cd $DATADIR && unzip "$BASENAME" -d "$NAME-gh"
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
  if [ "$MAVEN_HOME" = "" ]; then
    # not existent but probably is maven in the path?
    MAVEN_HOME=$(mvn -v | grep "Maven home" | cut -d' ' -f3,4,5,6)
    if [ "$MAVEN_HOME" = "" ]; then
      # try to detect previous downloaded version
      MAVEN_HOME="$GH_HOME/maven"
      if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
        echo "No Maven found in the PATH. Now downloading+installing it to $MAVEN_HOME"
        cd "$GH_HOME"
        MVN_PACKAGE=apache-maven-3.5.0
        wget -O maven.zip http://archive.apache.org/dist/maven/maven-3/3.5.0/binaries/$MVN_PACKAGE-bin.zip
        unzip maven.zip
        mv $MVN_PACKAGE maven
        rm maven.zip
      fi
    fi
  fi
}

function execMvn {
  "$MAVEN_HOME/bin/mvn" "$@" > /tmp/graphhopper-compile.log
  returncode=$?
  if [[ $returncode != 0 ]] ; then
    echo "## compilation of parent failed"
    cat /tmp/graphhopper-compile.log
    exit $returncode
  fi
}

function packageCoreJar {
  if [ ! -f "$JAR" ]; then
    echo "## building graphhopper jar: $JAR"
    echo "## using maven at $MAVEN_HOME"
    execMvn --projects tools -am -DskipTests=true package
  else
    echo "## existing jar found $JAR"
  fi
}

ensureMaven

## now handle actions which do not take an OSM file
if [ "$ACTION" = "clean" ]; then
 rm -rf ./android/app/target
 rm -rf ./*/target
 rm -rf ./target
 exit

elif [ "$ACTION" = "eclipse" ]; then
 packageCoreJar
 exit

elif [ "$ACTION" = "build" ]; then
 packageCoreJar
 exit  
 
elif [ "$ACTION" = "buildweb" ]; then
 execMvn --projects web -am -DskipTests=true package
 exit

elif [ "$ACTION" = "extract" ]; then
 echo use "./graphhopper.sh extract \"left,bottom,right,top\""
 URL="http://overpass-api.de/api/map?bbox=$2"
 #echo "$URL"
 wget -O extract.osm "$URL"
 exit
 
elif [ "$ACTION" = "android" ]; then
 execMvn -P include-android --projects android/app -am package android:deploy android:run
 exit
fi

if [ "$FILE" = "" ]; then
  echo -e "no file specified? try"
  printUsage
  exit
fi

# DATA_DIR = directories path to the file if any (if current directory, return .)
DATADIR=$(dirname "${FILE}")
# create the directories if needed
mkdir -p $DATADIR
# BASENAME = filename (file without the directories)
BASENAME=$(basename "${FILE}")
# NAME = file without extension if any
NAME="${BASENAME%.*}"

if [ "$FILE" == "-" ]; then
   OSM_FILE=
elif [ ${FILE: -4} == ".osm" ] || [ ${FILE: -4} == ".xml" ] || [ ${FILE: -4} == ".pbf" ]; then
   OSM_FILE="$FILE"
elif [ ${FILE: -7} == ".osm.gz" ]; then
   OSM_FILE="$FILE"
elif [ ${FILE: -3} == "-gh" ]; then
   OSM_FILE="$FILE"
   NAME=${FILE%%???}
elif [ ${FILE: -4} == ".ghz" ]; then
   OSM_FILE="$FILE"
   if [[ ! -d "$NAME-gh" ]]; then
      cd $DATADIR && unzip "$BASENAME" -d "$NAME-gh"
   fi
else
   # no known end -> no import
   OSM_FILE=
fi

GRAPH=$DATADIR/$NAME-gh
VERSION=$(grep  "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1)
JAR=tools/target/graphhopper-tools-$VERSION-jar-with-dependencies.jar

LINK=$(echo $NAME | tr '_' '/')
if [ "$FILE" == "-" ]; then
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

if [ "$JAVA_OPTS" = "" ]; then
  JAVA_OPTS="-Xmx1000m -Xms1000m -server"
fi

ensureOsm
packageCoreJar

echo "## now $ACTION. JAVA_OPTS=$JAVA_OPTS"

if [ "$ACTION" = "ui" ] || [ "$ACTION" = "web" ]; then
  export MAVEN_OPTS="$MAVEN_OPTS $JAVA_OPTS"
  if [ "$JETTY_PORT" = "" ]; then  
    JETTY_PORT=8989
  fi
  WEB_JAR="$GH_HOME/web/target/graphhopper-web-$VERSION-with-dep.jar"
  if [ ! -s "$WEB_JAR" ]; then
    execMvn --projects web -am -DskipTests=true package
  fi

  RC_BASE=./web/src/main/webapp

  if [ "$GH_FOREGROUND" = "" ]; then
    exec "$JAVA" $JAVA_OPTS -jar "$WEB_JAR" jetty.resourcebase=$RC_BASE \
	jetty.port=$JETTY_PORT jetty.host=$JETTY_HOST \
    	config=$CONFIG $GH_WEB_OPTS graph.location="$GRAPH" datareader.file="$OSM_FILE"
    # foreground => we never reach this here
  else
    exec "$JAVA" $JAVA_OPTS -jar "$WEB_JAR" jetty.resourcebase=$RC_BASE \
    	jetty.port=$JETTY_PORT jetty.host=$JETTY_HOST \
    	config=$CONFIG $GH_WEB_OPTS graph.location="$GRAPH" datareader.file="$OSM_FILE" <&- &
    if [ "$GH_PID_FILE" != "" ]; then
       echo $! > $GH_PID_FILE
    fi
    exit $?                    
  fi

elif [ "$ACTION" = "import" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" $GH_CLASS config=$CONFIG \
      $GH_IMPORT_OPTS graph.location="$GRAPH" datareader.file="$OSM_FILE"


elif [ "$ACTION" = "torture" ]; then
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.tools.QueryTorture $@


elif [ "$ACTION" = "miniui" ]; then
 execMvn --projects tools -am -DskipTests clean package
 JAR=tools/target/graphhopper-tools-$VERSION-jar-with-dependencies.jar   
 "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.ui.MiniGraphUI datareader.file="$OSM_FILE" config=$CONFIG \
              graph.location="$GRAPH"


elif [ "$ACTION" = "measurement" ]; then
 ARGS="config=$CONFIG graph.location=$GRAPH datareader.file=$OSM_FILE prepare.ch.weightings=fastest prepare.lm.weightings=fastest graph.flag_encoders=car prepare.min_network_size=10000 prepare.min_oneway_network_size=10000"
 # echo -e "\ncreate graph via $ARGS, $JAR"
 # START=$(date +%s)
 # avoid islands for measurement at all costs
 # "$JAVA" $JAVA_OPTS -cp "$JAR" $GH_CLASS $ARGS prepare.doPrepare=false prepare.minNetworkSize=10000 prepare.minOnewayNetworkSize=10000
 # END=$(date +%s)
 # IMPORT_TIME=$(($END - $START))

 function startMeasurement {
    execMvn --projects tools -am -DskipTests clean package
    COUNT=5000
    commit_info=$(git log -n 1 --pretty=oneline)
    echo -e "\nperform measurement via jar=> $JAR and ARGS=> $ARGS"
    "$JAVA" $JAVA_OPTS -cp "$JAR" com.graphhopper.tools.Measurement $ARGS measurement.count=$COUNT measurement.location="$M_FILE_NAME" \
            measurement.gitinfo="$commit_info"
 }
 
 
 # use all <last_commits> versions starting from HEAD
 last_commits=$3
  
 if [ "$last_commits" = "" ]; then
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
   
   startMeasurement
   echo -e "\nmeasurement.commit=$commit\n" >> "$M_FILE_NAME"
 done
 # revert checkout
 git checkout $current_commit
fi
