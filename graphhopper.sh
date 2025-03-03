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

FORCE_REDOWNLOAD=false
FORCE_GRAPH_CLEAR=true

# Update presigned url and file name to use a new file
if [ ! -f "/data/updated_osm_file_20241121_225129.pbf" ] || [ "$FORCE_REDOWNLOAD" = "true" ]; then
    if [ "$FORCE_REDOWNLOAD" = "true" ] && [ -f "/data/updated_osm_file_20241121_225129.pbf" ]; then
        echo "Force redownload enabled, deleting existing file..."
        rm "/data/updated_osm_file_20241121_225129.pbf"
    fi
    
    echo "File not found or redownload forced, downloading..."
    if curl -f -o /data/updated_osm_file_20241121_225129.pbf 'https://471112541871-ridesense-routing-data.s3.ap-south-1.amazonaws.com/updated_osm_file_20241121_225129.pbf?response-content-disposition=inline&X-Amz-Content-Sha256=UNSIGNED-PAYLOAD&X-Amz-Security-Token=IQoJb3JpZ2luX2VjEKT%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCmFwLXNvdXRoLTEiRzBFAiA0KfRiHPofDk0kK2e42vtBYkffW2Aa8JZklHazlol%2BbAIhAMqm%2BvXO3ozmR3XsBrt8LdZZGOwuIae3dKVPQr%2FMgBoTKu0DCN3%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQABoMNDcxMTEyNTQxODcxIgxKEEMyOFR0yF6zLTMqwQOtmYMdWcWDLFfE9bLRHVvtFEmYObK%2BDwAYLJGZqy5oUsNySNJUyhZ7PQmZU9EpGvUlbAK0Q9aNRnzdg%2FbDq%2BhDi%2B2SpMu1H%2FPshImQ0Aq2FlU6Pm7%2Bk%2FSodzPk1Ax%2BABw1f8L8FokLU2SahHa6e5C4MtVyBNIglI3Te0AGqMcn%2F19bntHx5VBq55AwmvEAy7VJqhgn8wvRfRTImeNRJPQ0HLF%2BlFUqS6dFWb9Ecx2a1wqd8h89cuREOMyxWApRTjr%2Fex1BWOPDxCgGw4QSZiqZDj1caXxAeq2fB%2BltvlLTYV88ppfqNnBnpHM8WuCZUZXU8u9g59BLsAit7aPLm664jo5NU9BF3Nw9rx59GQ9cHeEmP65gzlHp%2Bzz0%2Bi626kVzDhNQsGg05UELwI7fQlYS%2F4A9ngEMS%2BrMoXWb9hqm0o0gS8CegBbjGzOg7zFMqWH1C5O%2B0GfhvY8fGq88Ey99MQMd4xnmNEjt8Y2iRZ4wqxnprRuri9Hzwdf3Sjw%2FU0Jm3vkBfY1nRwA2ZgkCcWVWVe0UM1pawyRe%2FbI0xgAslzwwMAo9kxPNY5eJAzHknPUyJremsi%2B3gzImwy0xXA5yEjDo9pe%2BBjrkAjDrcoxDvsWKSK4%2FG7pek1mmlnJ3%2BsjK%2Fj4fq2kI9kTCf%2B%2B5zSG%2BVB8oV1%2B265bUMLDnM4%2FCXgFBo0GaDDlOQfK5QlOd8uDgzxpvUjY70Q4ITE%2FgrPJRTcZFy4PR3L1rCnYSeAMQasioxb0147%2Bkhx1IwZakbGEAyJL%2BPOz5dav%2FSXXX5kXEpSf8M98bRW64EgoIxP%2FA9PVhtqb6II%2Bab3ACMrkZs%2FspaapLvBoYIkdCEEqz4UIKZwznSFfDe5xU%2BUxBsUEfZcKe5g7bGa1edq8t6SZdPnzLVRb4Kc3f8164DUJ3ob6PumFO8S%2BmSlYmvkVs%2FZz39GR%2BG4%2FHEfsr9h7F8Yi8oK9IyI44kAHzLOTPLc5XHYTzxllB5fWh48MDCi32aNa62IbIRrPiBsEHZNHEL4wpIJav6ZmvjzdvWA0vqgVAE91kqDSwQ78zcECaSMlgCU%2BXWYrtLBvjN0Div8noBGUu&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=ASIAW3MD642X4PKMKBAJ%2F20250303%2Fap-south-1%2Fs3%2Faws4_request&X-Amz-Date=20250303T193658Z&X-Amz-Expires=21600&X-Amz-SignedHeaders=host&X-Amz-Signature=a5a45a3257ff655ee3146c2eeb8e2558c94998135d19d96ad5489d425a27ee0c'; then
        echo "Download successful"
    else
        echo "Download failed"
        exit 1
    fi
else
    echo "File already exists"
fi

if [ "$FORCE_GRAPH_CLEAR" = "true" ] && [ -d "/data/default-gh" ]; then
    echo "Force graph clear enabled, removing contents of /data/default-gh folder..."
    rm -rf /data/default-gh/*
fi

echo "## Executing $ACTION. JAVA_OPTS=$JAVA_OPTS"

echo "$JAVA" $JAVA_OPTS ${FILE:+-Ddw.graphhopper.datareader.file="$FILE"} -Ddw.graphhopper.graph.location="$GRAPH" \
        $GH_WEB_OPTS -jar "$JAR" $ACTION $CONFIG

exec "$JAVA" $JAVA_OPTS ${FILE:+-Ddw.graphhopper.datareader.file="$FILE"} -Ddw.graphhopper.graph.location="$GRAPH" \
        $GH_WEB_OPTS -jar "$JAR" $ACTION $CONFIG
