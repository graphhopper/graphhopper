
function set_jar_path {
  JAR=$(ls target/map-matching-*-dependencies.jar)
}

set_jar_path

if [ "$JAVA" = "" ]; then
 JAVA=java
fi

if [ ! -f "$JAR" ]; then
  mvn -DskipTests=true install assembly:single
  set_jar_path
fi

exec "$JAVA" $JAVA_OPTS -jar $JAR "$@"