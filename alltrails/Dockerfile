FROM maven:3.9.5-eclipse-temurin-21 as build

WORKDIR /graphhopper

COPY . .

RUN mvn clean install -DskipTests


FROM eclipse-temurin:21.0.1_12-jre

ENV JAVA_OPTS "-Xmx2g -Xms2g"

WORKDIR /graphhopper

COPY --from=build /graphhopper/web/target/graphhopper*.jar ./

COPY alltrails/config/graphhopper.sh alltrails/config/config-alltrails.yml ./

EXPOSE 8989 8990

HEALTHCHECK --interval=5s --timeout=3s CMD curl --fail http://localhost:8989/health || exit 1

ENTRYPOINT [ "./graphhopper.sh", "-c", "config-alltrails.yml", "-o", "/graphhopper/data/default-gh" ]
