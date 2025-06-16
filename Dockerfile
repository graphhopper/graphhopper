# Build stage
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /graphhopper

COPY . .

RUN mvn clean install -DskipTests

# Run stage
FROM eclipse-temurin:21.0.1_12-jre

WORKDIR /graphhopper

# Copy built jar and config
COPY --from=build /graphhopper/web/target/graphhopper-web-*.jar ./graphhopper.jar
COPY config-example.yml ./

ENV JAVA_OPTS="-Xmx4g -Xms4g"

# Enable external access
RUN sed -i 's/^# *bind_host:.*/bind_host: 0.0.0.0/' config-example.yml

VOLUME ["/data"]

EXPOSE 8989 8990

HEALTHCHECK --interval=10s --timeout=5s \
  CMD curl --fail http://localhost:8989/health || exit 1

ENV JAVA_OPTS="-Xmx4g -Xms4g"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS \"$@\"", "--"]

CMD ["-Ddw.graphhopper.datareader.file=/data/norway-latest.osm.pbf", "-Ddw.graphhopper.graph.location=/data/graph-cache", "-jar", "graphhopper.jar", "server", "config-example.yml"]
