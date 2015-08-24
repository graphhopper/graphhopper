set_jar_path

function set_jar_path {
  JAR=$(ls target/map-matching-*-dependencies.jar)
}

if [ "$JAVA" = "" ]; then
 JAVA=java
fi

if [ ! -f "$JAR" ]; then
  mvn -DskipTests=true install assembly:single
  set_jar_path
fi

exec "$JAVA" $JAVA_OPTS -jar $JAR "$@"