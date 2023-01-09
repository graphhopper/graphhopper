FROM maven:3.6.3-jdk-8 as build

RUN apt-get install -y wget
WORKDIR /graphhopper
COPY . .
RUN mvn clean install -DskipTests

FROM openjdk:11.0-jre

RUN apt-get update \
  && apt-get install -y awscli pigz \
  && mkdir -p /data

WORKDIR /graphhopper
COPY --from=build /graphhopper/web/target/graphhopper*.jar ./
RUN chmod +x *.jar

RUN mkdir -p /bay-area
COPY ./bay-area/* ./bay-area/

COPY ./build-graph-cache.sh .
RUN chmod +x ./build-graph-cache.sh

VOLUME [ "/data" ]

EXPOSE 8989

HEALTHCHECK --interval=5s --timeout=3s CMD curl --fail http://localhost:8989/health || exit 1

ENTRYPOINT [ "./build-graph-cache.sh" ]
