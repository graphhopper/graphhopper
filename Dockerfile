FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /graphhopper

COPY . .

RUN mvn install -DskipTests

FROM eclipse-temurin:21.0.1_12-jre

# 7-8GB has determined to be the minimum required for import
ENV JAVA_OPTS="-Xmx8g -Xms5g"

RUN mkdir -p /data

WORKDIR /graphhopper

COPY --from=build /graphhopper/web/target/graphhopper*.jar ./

COPY graphhopper.sh config-example.yml config-rs.yml ./

# Enable connections from outside of the container
RUN sed -i '/^ *bind_host/s/^ */&# /p' config-example.yml

VOLUME [ "/data" ]

EXPOSE 8989 8990

HEALTHCHECK --interval=5s --timeout=3s CMD curl --fail http://localhost:8989/health || exit 1

ENTRYPOINT [ "./graphhopper.sh", "-c", "config-rs.yml"]