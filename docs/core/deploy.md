# Deploy Guide

This guide is written for everyone interested in deploying graphhopper on a server.
 
## Basics
 
For simplicity you could just start jetty from maven and schedule it as background job: 
`export GH_FOREGROUND=false && export JETTY_PORT=11111 && ./graphhopper.sh web europe_germany_berlin.pbf`. 
Then the service will be accessible on port 11111.

For production usage you can install the latest jetty (at least 9) as a service but we prefer to have it bundled as a 
simple jar. Tomcat should work too. To create a war file do `mvn clean install war:war` and copy it from the target/ 
folder to your jetty installation. Then copy web/config.properties also there and change this properties 
file to point to the required graphhopper folder. Increase the Xmx/Xms values of your jetty server e.g. 
for world wide coverage with a hierarchical graph do the following in bin/jetty.sh
```
bash
export JAVA=java-home/bin/java
export JAVA_OPTIONS="-server -Xconcurrentio -Xmx17000m -Xms17000m"
```

Important notes:

 * jsonp support needs to be enabled in the config.properties
 * none-hierarchical graphs should be limited to a certain distance otherwise you'll require lots of RAM per request! See [#104](https://github.com/graphhopper/graphhopper/issues/104)
 * if you have strange speed problems which could be related to low memory you can try to [entire disable swap](http://askubuntu.com/questions/103915/how-do-i-configure-swappiness). Or just try it out via `sudo swapoff -a`. Swapping out is very harmful to Java programs especially when the GC kicks in.

### API Tokens

By default, GraphHopper uses [Omniscale](http://omniscale.com/) as layer service. 
Either you get a plan there or you have to remove Omniscale from the [JavaScript file](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/webapp/js/main.js). 
 
GraphHopper uses the [GraphHopper Directions API](https://graphhopper.com/api/1/docs/) for geocoding. To be able to use the autocomplete feature of the point inputs you have to:

 * Get your API Token at: https://graphhopper.com/
 * Uncomment this [line](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/webapp/js/main-template.js#L37) and insert your API Token as second parameter.
   * Don't forget the Attribution
 
## World Wide 

GraphHopper is able to handle coverage for the whole [Openstreetmap road network](http://planet.osm.org/). 
It needs approximately 22GB RAM for the import (CAR only) and ~1 hour (plus ~5h for contraction). 
If you can accept slower import times this can be reduced to 14GB RAM - you'll need to set osmreader.dataaccess=MMAP

Then, to run the web service with this world wide graph 'only' 15GB are necessary. Without contraction hierarchy 
this would be about 9GB.

With CH the service is able to handle about 180 queries per second (from localhost to localhost this was 300qps). 
Measured for CAR routing, real world requests, at least 100km long, on a linux machine with 8 cores and 32GB, 
java 1.7.0_25, jetty 8.1.10 via custom QueryTorture class (10 worker threads).

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


### TODOs

 * Try out to disable NUMA -> http://engineering.linkedin.com/performance/optimizing-linux-memory-management-low-latency-high-throughput-databases
