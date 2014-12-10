VERSION=0.4-SNAPSHOT
JAR=target/map-matching-0.4-SNAPSHOT-jar-with-dependencies.jar
#target/graphhopper-map-matching-$VERSION-jar-with-dependencies.jar

if [ "$JAVA" = "" ]; then
 JAVA=java
fi

if [ ! -f "$JAR" ]; then
  mvn -DskipTests=true install assembly:single
fi

exec "$JAVA" $JAVA_OPTS -jar $JAR "$@"