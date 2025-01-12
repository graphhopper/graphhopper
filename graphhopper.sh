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

function printBashUsage {
  echo "$(basename $0): Start a Gpahhopper server."
  echo "Default user access at 0.0.0.0:8989 and API access at 0.0.0.0:8989/route"
  echo ""
  echo "Usage"
  echo "$(basename $0) [<parameter> ...] "
  echo ""
  echo "parameters:"
  echo "-i | --input <osm-file>   OSM local input file location"
  echo "--url <url>               download input file from a url and save as data.pbf"
  echo "--import                  only create the graph cache, to be used later for faster starts"
  echo "-c | --config <config>    application configuration file location"
  echo "-o | --graph-cache <dir>  directory for graph cache output"
  echo "-p | --profiles <string>  comma separated list of vehicle profiles"
  echo "--port <port>             port for web server [default: 8989]"
  echo "--host <host>             host address of the web server [default: 0.0.0.0]"
  echo "-h | --help               display this message"
}

# one character parameters have one minus character'-'. longer parameters have two minus characters '--'
while [ ! -z $1 ]; do
  case $1 in
    --import) ACTION=import; shift 1;;
    -c|--config) CONFIG="$2"; shift 2;;
    -i|--input) FILE="$2"; shift 2;;
    --url) URL="$2"; shift 2;;
    -o|--graph-cache) GRAPH="$2"; shift 2;;
    -p|--profiles) GH_WEB_OPTS="$GH_WEB_OPTS -Ddw.graphhopper.graph.flag_encoders=$2"; shift 2;;
    --port) GH_WEB_OPTS="$GH_WEB_OPTS -Ddw.server.application_connectors[0].port=$2"; shift 2;;
    --host) GH_WEB_OPTS="$GH_WEB_OPTS -Ddw.server.application_connectors[0].bind_host=$2"; shift 2;;
    -h|--help) printBashUsage
        exit 0;;
    -*) echo "Option unknown: $1"
        echo
        printBashUsage
        exit 2;;
  esac
done

# Defaults
: "${ACTION:=server}"
: "${GRAPH:=/data/default-gh}"
: "${CONFIG:=config-example.yml}"
: "${JAVA_OPTS:=-Xmx8g -Xms6g}"
: "${JAR:=$(find . -type f -name "*.jar")}"

if [ "$URL" != "" ]; then
  wget -S -nv -O "${FILE:=data.pbf}" "$URL"
fi

# create the directories if needed
mkdir -p $(dirname "${GRAPH}")

echo "## Loading data"
# /usr/local/bin/aws s3 cp s3://471112541871-ridesense-routing-data/updated_osm_file_20241121_225129.pbf /data/updated_osm_file_20241121_225129.pbf

# Presigned url available for 4 hours from 23:39 IST 4th Jan 2025
if [ ! -f "/data/updated_osm_file_20241121_225129.pbf" ]; then
    echo "File not found, downloading..."
    if curl -f -o /data/updated_osm_file_20241121_225129.pbf 'https://471112541871-ridesense-routing-data.s3.ap-south-1.amazonaws.com/updated_osm_file_20241121_225129.pbf?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEDIaCmFwLXNvdXRoLTEiRjBEAiB0o2cHh0kokY6C5o0QlCO3o2Dn65xEn%2Fqqo2vK2nQzaAIgPh%2FtPW6g4fC%2BjZgTvxBhA%2B9h%2BzWFIONUHKg0smDW8Ooq5AMIGxAAGgw0NzExMTI1NDE4NzEiDNjm4ZKplWguvh2pCCrBA2o4b0hEKfMBuC1ZNi5kunRW%2B7s6v0cJsdYq2%2F%2BCobin%2FRa9DeZIaVxNwlurSgEFN94suDtAJKkSW96vMSxrifHDFmY7k4FxceMK1xg35%2BXt%2BJasfCCh1dSCmg7PKtFwh8jWEr0aXVxfLnex9tpmu7qcJIRXFKDcXDPdmVJr5BtiPehYtm8hJ5op1CxjFYS2DaJAEKK46OxOhCnHaL36Kpc8pxQzmyZ2OO%2FOUin0TJzdNY%2F6HhVZu2G60W82gHEXPbdYJRs02LSlTLAzWxRisCMAzzXKBroQLW%2F7Gr1AvNFENJmLkncowWkq4UpqnceG0vnm4ClZEKbasbpZLJ9Q2vC0DYY2sH%2F9vSTS1WHLTKEVMvTl2fjqSJS0Y3ZYTkMe4tTet%2F5oceGIMXuYsrhcDIrCH8TE5NeGJ3t1plv4hTrmWlNU5t7zHbaa95hnuUH2zD6Oipe84Rl56SIwdx0l4O3xsx2%2FVKgOhJ%2Bm9gyHmXVcquVYMDrofCS126DCFZKPRfGM1jbGqUjlLFmT9GGMB8UN2ax98f7K2SiSn87V0Srlkk2LyEOedOLKr4kWyOgPkmjUO2ry3ub%2FWOwlK7hHfPx%2FMNm15bsGOuUCS6SSNmCsoVJdYZtYI8DFFM2q7n1PlGHfXzd7Ij3ziWSKJHVuNdlWzAlf8Yb%2BGbCvKCu10F1rVjtnG4OjTrYfLd21AnDaE4XUbH%2FRUe7c4jVBqtPgCqlykUyRRsER%2BykJYobiSwhQVJrIp3AvItZc07zy%2Fweo6g7LrcLLz0zNEAXKpwpaZT4Qd64CgTOJKEEQom7kInWpM3%2BhMGDsg9iPXktwQcY1hWSueRRQEZ0NHKDZQdpEy0Lt%2FWZjFoXfbJ8kfaqZGK81jcE5NIghbkNOf%2BMv1zfbM%2BG%2B0kP0zAdelj6tmCfc1DWhnLyqOcFcwm%2Bt09H5bVI7pW3Xw%2F2sTGWHSIjQUbwJIcKs%2BOT1%2FhSTjLT98ECqdZ2cH7%2FTlQqHUv48GpPKM6rDM1cAhdVlkR7mpm%2F%2FcDZ8pXVFtzXdX8fF7DfxXtj5KFEwKN1N6SiLN1kWSJ1VuDXpBi%2FGGbwLmpkijZh6KlTG&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIAW3MD642XWQB5XDB6%2F20250104%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250104T180837Z&X-Amz-Expires=14400&X-Amz-SignedHeaders=host&X-Amz-Signature=cb111151029e757e60c26fad9622880ff2272bb441deccc4541022f908d6517d'; then
        echo "Download successful"
    else
        echo "Download failed"
        exit 1
    fi
else
    echo "File already exists"
fi

echo "## Executing $ACTION. JAVA_OPTS=$JAVA_OPTS"

echo "$JAVA" $JAVA_OPTS ${FILE:+-Ddw.graphhopper.datareader.file="$FILE"} -Ddw.graphhopper.graph.location="$GRAPH" \
        $GH_WEB_OPTS -jar "$JAR" $ACTION $CONFIG

exec "$JAVA" $JAVA_OPTS ${FILE:+-Ddw.graphhopper.datareader.file="$FILE"} -Ddw.graphhopper.graph.location="$GRAPH" \
        $GH_WEB_OPTS -jar "$JAR" $ACTION $CONFIG