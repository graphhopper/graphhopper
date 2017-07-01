FROM openjdk:8-jdk

RUN mkdir -p /data
RUN mkdir -p /graphhopper

COPY . /graphhopper/

WORKDIR /graphhopper

RUN ./graphhopper.sh buildweb
