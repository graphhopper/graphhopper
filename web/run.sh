export MAVEN_OPTS="$MAVEN_OPTS -Xmx1600m -Xms1600m -XX:PermSize=40m -XX:MaxPermSize=40m"; mvn -Djetty.reload=manual jetty:run
