#!/bin/bash

if [ "$JAVA_HOME" != "" ]; then
 JAVA=$JAVA_HOME/bin/java
fi

if [ "$JAVA" = "" ]; then
 JAVA=java
fi

echo "using $JAVA"

if [ "$1" = "action=start-server" ]; then
  function set_jar_path {
    JAR=$(ls matching-web/target/graphhopper-map-matching-web-*-dependencies.jar)
  }

  set_jar_path

  if [ ! -f "$JAR" ]; then
    mvn --non-recursive install
    mvn -am --projects matching-web -DskipTests=true install
    mvn --projects matching-web -DskipTests=true install assembly:single
    set_jar_path
  fi
  
  ARGS="graph.location=./graph-cache jetty.resourcebase=matching-web/src/main/webapp"
  
elif [ "$1" = "action=test" ]; then

  export MAVEN_OPTS="-Xmx400m -Xms400m"
  mvn clean test verify
  # return exit code of mvn
  exit $?
  
else
  function set_jar_path {
    JAR=$(ls matching-core/target/graphhopper-map-matching-*-dependencies.jar)
  }

  set_jar_path

  if [ ! -f "$JAR" ]; then
    mvn --non-recursive install
    mvn -am --projects matching-core -DskipTests=true install
    mvn --projects matching-core -DskipTests=true install assembly:single
    set_jar_path
  fi
  
  ARGS="$@"
fi

exec "$JAVA" $JAVA_OPTS -jar $JAR $ARGS prepare.min_network_size=0 prepare.min_one_way_network_size=0
