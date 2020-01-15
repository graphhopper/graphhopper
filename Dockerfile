FROM openjdk:8-jdk

ENV JAVA_OPTS "-server -Xconcurrentio -Xmx1g -Xms1g -XX:+UseG1GC -Ddw.server.applicationConnectors[0].bindHost=0.0.0.0 -Ddw.server.applicationConnectors[0].port=8989"

RUN mkdir -p /data && mkdir -p /graphhopper

# install node - only required for JS UI
RUN apt-get install -y wget \
       && curl -sL https://deb.nodesource.com/setup_11.x | bash - \
       && apt-get install -y nodejs

COPY . /graphhopper/

WORKDIR /graphhopper

# create main.js - only required for JS UI
RUN cd web && npm install && npm run bundleProduction && cd ..

RUN ./graphhopper.sh build

VOLUME [ "/data" ]

EXPOSE 8989

ENTRYPOINT [ "./graphhopper.sh", "web" ]

CMD [ "/data/europe_germany_berlin.pbf" ]
