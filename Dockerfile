FROM openjdk:8-jdk

RUN mkdir -p /data

COPY . /graphhopper/

RUN cd /graphhopper && \
    ./graphhopper.sh buildweb

WORKDIR /graphhopper
VOLUME ["/data"]

EXPOSE 8989
