# Define platform as build arg
ARG TARGETPLATFORM=linux/arm64

FROM --platform=${TARGETPLATFORM} maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /graphhopper

COPY . .

RUN mvn install -DskipTests

FROM --platform=${TARGETPLATFORM} eclipse-temurin:21.0.1_12-jre

# Takes about 16 GB for creating the cache, ECS will control how much memory to allocate at runtime
ENV JAVA_OPTS="-Xmx20g -Xms16g"

RUN mkdir -p /data

WORKDIR /graphhopper

COPY --from=build /graphhopper/web/target/graphhopper*.jar ./

COPY graphhopper.sh config-rs.yml ./

VOLUME [ "/data" ]

ARG PBF_PATH
COPY ${PBF_PATH} /data/

RUN ./graphhopper.sh --import --data /data/$(basename "${PBF_PATH}") -c config-rs.yml && rm /data/$(basename "${PBF_PATH}")

EXPOSE 8989

HEALTHCHECK --interval=60s --timeout=10s CMD curl --fail http://localhost:8989/health || exit 1

ENTRYPOINT [ "./graphhopper.sh", "-c", "config-rs.yml", "--host", "0.0.0.0"]
