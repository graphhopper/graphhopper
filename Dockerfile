FROM openjdk:8-jdk

ENV JAVA_OPTS "-server -Xconcurrentio -Xmx1g -Xms1g -XX:+UseG1GC -Ddw.server.applicationConnectors[0].bindHost=0.0.0.0 -Ddw.server.applicationConnectors[0].port=8989"

RUN mkdir -p /data && mkdir -p /graphhopper

RUN apt-get install -y wget \
    && wget -qO- https://raw.githubusercontent.com/nvm-sh/nvm/v0.34.0/install.sh | bash && \. $HOME/.nvm/nvm.sh \
    && nvm install node

COPY . /graphhopper/

WORKDIR /graphhopper

RUN cd web && npm install && npm run bundleProduction && cd ..

RUN ./graphhopper.sh build

VOLUME [ "/data" ]

EXPOSE 8989

ENTRYPOINT [ "./graphhopper.sh", "web" ]

CMD [ "/data/europe_germany_berlin.pbf" ]
