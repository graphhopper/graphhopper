GraphHopper is able to handle coverage for the whole [Openstreetmap road network](http://planet.osm.org/). 
It needs approximately 22GB RAM for the import (CAR only) and ~1 hour (plus ~5h for contraction). 
If you can accept slower import times this can be reduced to 14GB RAM - you'll need to set osmreader.dataaccess=MMAP

Then, to run the web service with this world wide graph 'only' 15GB are necessary. Without contraction hierarchy 
this would be about 9GB.

With CH the service is able to handle about 180 queries per second (from localhost to localhost this was 300qps). 
Measured for CAR routing, real world requests, at least 100km long, on a linux machine with 8 cores and 32GB, 
java 1.7.0_25, jetty 8.1.10 via custom QueryTorture class (10 worker threads).

## JVM

If GC pauses are too long try `-XX:+UseG1GC`

## Elevation Data 

If you want to use elevation data you need to increase the allowed number of open files. Under linux this works as follows:

 * sudo vi /etc/security/limits.conf
 * add: `* - nofile 100000`
   which means set hard and soft limit of "number of open files" for all users to 100K
 * sudo vi /etc/sysctl.conf
 * add: `fs.file-max = 90000`
 * reboot now (or sudo sysctl -p; and re-login)
 * afterwards `ulimit -Hn` and `ulimit -Sn` should give you 100000


## TODOs

 * Try out to disable NUMA -> http://engineering.linkedin.com/performance/optimizing-linux-memory-management-low-latency-high-throughput-databases