FROM maven:3.8.4-jdk-11 AS builder

WORKDIR /build

COPY . /build

RUN mvn clean install

FROM amazoncorretto:11.0.14

RUN \
    yum -y install shadow-utils curl \
    && mkdir -p /app \
    && useradd -m squirrel

COPY --from=builder /build/web/target/*.jar /app/
COPY --from=builder /build/docker/entrypoint.sh /app/

WORKDIR /app

EXPOSE 8080

USER squirrel


# which BPF file to download
ENV GH_PBF_SOURCE https://download.geofabrik.de/europe/germany/berlin-latest.osm.bpf

# where to put downloaded / built files.
# this should be a docker volume, or a PV under kube, if you want to keep data from one run to another
ENV GH_DATA_VOLUME /data

# config file
ENV GH_CONFIG_FILE /app/config/config.yml


ENTRYPOINT ["/app/entrypoint.sh"]

# labels for bbc command
LABEL name="lelab_graphhopper"
LABEL build_path="../"
LABEL version_auto_semver="true"
