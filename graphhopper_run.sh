#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required for handling Windows cr/lf 
# See StackOverflow answer http://stackoverflow.com/a/14607651

GH_HOME=$(dirname "$0")
JAVA=$JAVA_HOME/bin/java
if [ "$JAVA_HOME" = "" ]; then
 JAVA=java
fi

vers=$($JAVA -version 2>&1 | grep "version" | awk '{print $3}' | tr -d \")
bit64=$($JAVA -version 2>&1 | grep "64-Bit")
if [ "$bit64" != "" ]; then
  vers="$vers (64bit)"
fi
echo "## using java $vers from $JAVA_HOME"

RUN_ENV=$5

function printBashUsage {
  echo "Usage:"
  echo "-a | --action <action>    must be one the following actions:"
  echo "     --action import      creates the graph cache only, used for later faster starts"
  echo "     --action web         starts a local server for user access at localhost:8989 and API access at localhost:8989/route"
  echo "     --action build       creates the graphhopper web JAR"
  echo "     --action clean       removes all JARs, necessary if you need to use the latest source (e.g. after switching the branch etc)"
  echo "     --action measurement does performance analysis of the current source version via random routes (Measurement class)"
  echo "     --action torture     can be used to test real world routes via feeding graphhopper logs into a GraphHopper system (Torture class)"
  echo "-c | --config <config>    specify the application configuration"
  echo "-d | --run-background     run the application in background (detach)"
  echo "-fd| --force-download     force the download of the OSM data file if needed"
  echo "-h | --help               display this message"
  echo "--host <host>             specify to which host the service should be bound"
  echo "-i | --input <file>       path to the input map file or name of the file to download"
  echo "--jar <file>              specify the jar file (useful if you want to reuse this script for custom builds)"
  echo "-o | --graph-cache <dir>  directory for graph cache output"
  echo "-p | --profiles <string>  comma separated list of vehicle profiles"
  echo "--port <port>             start web server at specific port"
  echo "-v | --version            print version"
}

VERSION=$(grep "<name>" -A 1 pom.xml | grep version | cut -d'>' -f2 | cut -d'<' -f1)

# one or two character parameters have one minus character'-' all longer parameters have two minus characters '--'
while [ ! -z $1 ]; do
  case $1 in
    -a|--action) ACTION=$2; shift 2;;
    -c|--config) CONFIG="$2"; shift 2;;
    -d|--run-background) RUN_BACKGROUND=true; shift 1;;
    -fd|--force-download) FORCE_DWN=1; shift 1;;
    -h|--help) printBashUsage
      exit 0;;
    --host) GH_WEB_OPTS="$GH_WEB_OPTS -Ddw.server.applicationConnectors[0].bindHost=$2"; shift 2;;
    -i|--input) FILE="$2"; shift 2;;
    --jar) JAR="$2"; shift 2;;
    -o|--graph-cache) GRAPH="$2"; shift 2;;
    -p|--profiles) GH_WEB_OPTS="$GH_WEB_OPTS -Dgraphhopper.graph.flag_encoders=$2"; shift 2;;
    --port) GH_WEB_OPTS="$GH_WEB_OPTS -Ddw.server.applicationConnectors[0].port=$2"; shift 2;;
    -v|--version) echo $VERSION
    	exit 2;;
    # forward VM options, here we assume no spaces ie. just one parameter!?
    -D*)
       GH_WEB_OPTS="$GH_WEB_OPTS $1"; shift 1;;
    # forward parameter via replacing first two characters of the key with -Dgraphhopper.
    *=*)
       echo "Old parameter assignment not allowed $1"; exit 2;;
    --*)
       GH_WEB_OPTS="$GH_WEB_OPTS -Dgraphhopper.${1:2}=$2"; shift 2;;
    -*) echo "Option unknown: $1"
        echo
        printBashUsage
	exit 2;;
    # backward compatibility
    *) REMAINING_ARGS+=($1); shift 1;;
  esac
done

if [ -z $ACTION ]; then
  ACTION=${REMAINING_ARGS[0]}
fi

if [ -z $FILE ]; then
  FILE=${REMAINING_ARGS[1]}
fi

if [ "$ACTION" = "" ]; then
 echo "## action $ACTION not found!"
 printBashUsage
fi

if [[ "$CONFIG" == *properties ]]; then
 echo "$CONFIG not allowed as configuration. Use yml"
 exit
fi

# default init, https://stackoverflow.com/a/28085062/194609
: "${CONFIG:=config.yml}"
if [ ! -f "config.yml" ]; then
  cp config-example.yml $CONFIG
fi

if [ "$FILE" = "" ]; then
  echo -e "no file specified?"
  printBashUsage
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

LINK=$(echo $NAME | tr '_' '/')
if [ "$FILE" == "-" ]; then
   LINK=
elif [ ${FILE: -4} == ".osm" ]; then 
   LINK="http://download.geofabrik.de/$LINK-latest.osm.bz2"
elif [ ${FILE: -4} == ".ghz" ]; then
   LINK="https://graphhopper.com/public/maps/0.1/$FILE"
elif [ ${FILE: -4} == ".pbf" ]; then
   LINK="http://download.geofabrik.de/$LINK-latest.osm.pbf"
else
   # e.g. if directory ends on '-gh'
   LINK="http://download.geofabrik.de/$LINK-latest.osm.pbf"
fi

: "${JAVA_OPTS:=-Xmx5000m -Xms3500m}"
: "${JAR:=graphhopper-web-0.11-SNAPSHOT.jar}"
: "${GRAPH:=$DATADIR/$NAME-gh}"

echo "## now $ACTION. JAVA_OPTS=$JAVA_OPTS"

if [[ "$ACTION" = "web" ]]; then
  export MAVEN_OPTS="$MAVEN_OPTS $JAVA_OPTS"
  if [[ "$RUN_BACKGROUND" == "true" ]]; then
    exec "$JAVA" -javaagent:newrelic.jar -Dnewrelic.environment=${RUN_ENV} $JAVA_OPTS -Dgraphhopper.datareader.file="./osrm_location.osm.pbf" -Dgraphhopper.graph.location="./osrm_location.osm-gh" \
                 $GH_WEB_OPTS -jar "$JAR" server $CONFIG <&- &
    
    if [[ "$GH_PID_FILE" != "" ]]; then
       echo $! > $GH_PID_FILE
    fi
    exit $?
  else
    exec "$JAVA" -javaagent:newrelic.jar -Dnewrelic.environment=${RUN_ENV} $JAVA_OPTS -Dgraphhopper.datareader.file="./osrm_location.osm.pbf" -Dgraphhopper.graph.location="./osrm_location.osm-gh" \
                 $GH_WEB_OPTS -jar "$JAR" server $CONFIG
    # foreground => we never reach this here
  fi
fi
