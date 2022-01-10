# Deploy Guide

This guide is written for everyone interested in deploying graphhopper on a server.

## Basics

See the [installation section](../../README.md#installation) on how to start the server.
Embed this command in a shell script and use this from e.g. [Docker](../../README.md#docker) or systemd.

For production usage you have a web service included where you can use [this configuration](https://raw.githubusercontent.com/graphhopper/graphhopper/master/config-example.yml)
Increase the -Xmx/-Xms values of your server accordingly.

If you use JDK11 you should try a different garbage collectors like G1, ZGC or Shenandoah.
The G1 is the default but the other two GCs are better suited for JVMs with bigger heaps (>32GB) and low pauses.
You enabled them with `-XX:+UseZGC` or `-XX:+UseShenandoahGC`. Please note that especially ZGC and G1 require quite a
bit memory additionally to the heap and so sometimes speed can be increased when you lower the `Xmx` value.

Notes:

 * If you want to support none-CH requests you should at least enable landmarks or limit the request to a certain
 * distance otherwise it might require lots of RAM per request! See [#734](https://github.com/graphhopper/graphhopper/issues/734).

### API Tokens

By default, the GraphHopper UI uses [Omniscale](http://omniscale.com/) and/or [Thunderforest](http://thunderforest.com/) as layer service.
Either you get a plan there, then set the API key in the options.js file, or you
have to remove Omniscale from the [JavaScript file](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/resources/com/graphhopper/maps/js/map.js).

GraphHopper uses the [GraphHopper Directions API](https://docs.graphhopper.com/#tag/Geocoding-API) for geocoding.
To be able to use the autocomplete feature of the point inputs you have to:

 * Get your API Token at: https://www.graphhopper.com/ and set this in the options.js
 * Don't forget the Attribution when using the free package

## Worldwide Setup

GraphHopper is able to handle coverage for the whole [OpenStreetMap road network](http://planet.osm.org/).
It needs approximately 25GB RAM for the import (CAR only) and ~1 hour (plus ~5h for contraction).
If you can accept slower import times this can be reduced to 14GB RAM - you'll need to set `datareader.dataaccess=MMAP` in the config file.

Then 'only' 15GB are necessary and without contraction hierarchy this can be further reduced to about 9GB.

### System tuning

Avoid swapping e.g. on linux via `vm.swappiness=0` in /etc/sysctl.conf. See some tuning discussion in the answers [here](http://stackoverflow.com/q/38905739/194609).

When using the MMAP setting (default for elevation data), then ensure `/proc/sys/vm/max_map_count` is enough or set it via `sysctl -w vm.max_map_count=500000`. see also https://github.com/graphhopper/graphhopper/issues/1866.

### Elevation Data

If you want to use elevation data you need to increase the allowed number of open files. Under linux this works as follows:

 * sudo vi /etc/security/limits.conf
 * add: `* - nofile 100000`
   which means set hard and soft limit of "number of open files" for all users to 100K
 * sudo vi /etc/sysctl.conf
 * add: `fs.file-max = 90000`
 * reboot now (or sudo sysctl -p; and re-login)
 * afterwards `ulimit -Hn` and `ulimit -Sn` should give you 100000
 
