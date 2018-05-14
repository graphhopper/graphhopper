# Deploy Guide

This guide is written for everyone interested in deploying graphhopper on a server.

## Basics

For simplicity you could just start jetty from maven and schedule it as background job:
`./graphhopper.sh -a web -i europe_germany_berlin.pbf -d --port 11111`.
Then the service will be accessible on port 11111.

For production usage you have a web service included. Copy [this configuration](https://raw.githubusercontent.com/graphhopper/graphhopper/master/config-example.yml)
also there and use `-c config.yml` in the script to point to it. Increase the -Xmx/-Xms values of your server server e.g.
for world wide coverage with a hierarchical graph do the following before calling graphhopper.sh:

```
export JAVA_OPTS="-server -Xconcurrentio -Xmx17000m -Xms17000m"
```

Notes:

 * none-hierarchical graphs should be limited to a certain distance otherwise you'll require lots of RAM per request! See [#104](https://github.com/graphhopper/graphhopper/issues/734) or use landmarks.

### API Tokens

By default, GraphHopper uses [Omniscale](http://omniscale.com/) and/or [Thunderforest](http://thunderforest.com/) as layer service.
Either you get a plan there, then set the API key in the options.js file or you
have to remove Omniscale from the [JavaScript file](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/resources/assets/js/main.js).

GraphHopper uses the [GraphHopper Directions API](https://graphhopper.com/api/1/docs/) for geocoding. To be able to use the autocomplete feature of the point inputs you have to:

 * Get your API Token at: https://graphhopper.com/ and set this in the options.js
 * Don't forget the Attribution

## World Wide

GraphHopper is able to handle coverage for the whole [OpenStreetMap road network](http://planet.osm.org/).
It needs approximately 22GB RAM for the import (CAR only) and ~1 hour (plus ~5h for contraction).
If you can accept slower import times this can be reduced to 14GB RAM - you'll need to set datareader.dataaccess=MMAP

Then 'only' 15GB are necessary. Without contraction hierarchy this would be about 9GB.

With CH the service is able to handle about 180 queries per second (from localhost to localhost this was 300qps).
Measured for CAR routing, real world requests, at least 100km long, on a linux machine with 8 cores and 32GB,
java 1.7.0_25, jetty 8.1.10 via the QueryTorture class (10 worker threads).

### System and JVM tuning

Especially for large heaps you should use `-XX:+UseG1GC`. Optionally add `-XX:MetaspaceSize=100M`.

Avoid swapping e.g. on linux via `vm.swappiness=0` in /etc/sysctl.conf. See some tuning discussion in the answers [here](http://stackoverflow.com/q/38905739/194609).

### Elevation Data

If you want to use elevation data you need to increase the allowed number of open files. Under linux this works as follows:

 * sudo vi /etc/security/limits.conf
 * add: `* - nofile 100000`
   which means set hard and soft limit of "number of open files" for all users to 100K
 * sudo vi /etc/sysctl.conf
 * add: `fs.file-max = 90000`
 * reboot now (or sudo sysctl -p; and re-login)
 * afterwards `ulimit -Hn` and `ulimit -Sn` should give you 100000
