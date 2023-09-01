FROM maven:3.9.4-amazoncorretto-20 as build

RUN yum install -y wget git tar
WORKDIR /graphhopper
COPY . .
RUN mvn clean install -DskipTests

FROM amazoncorretto:20-al2-jdk

RUN yum install -y awscli pigz tar && \
    mkdir -p /data

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
