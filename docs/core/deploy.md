# Deploy Guide

This guide is written for everyone interested in deploying graphhopper on a server.

## Basics

See the [installation section](../../README.md#installation) on how to start the server.

Then you can embed these commands in a shell script and use this from e.g. [Docker](../../README.md#docker) or systemd.

For production usage you have a web service included where you can use [this configuration](https://raw.githubusercontent.com/graphhopper/graphhopper/master/config-example.yml)
Increase the -Xmx/-Xms parameters of the command accordingly.

You can reduce the memory requirements for the import step when you run the
"import" command explicitly before the "server" command:

```
java [options] -jar *.jar import config.yml
java [options] -jar *.jar server config.yml # calls the import command implicitly, if not done before
```

Try a different garbage collectors (GCs) like ZGC or Shenandoah for serving the
routing requests. The G1 is the default GC but the other two GCs are better suited for JVMs with bigger heaps (>32GB) and low pauses.
You enabled them with `-XX:+UseZGC` or `-XX:+UseShenandoahGC`. Please note that especially ZGC and G1 require quite a
bit memory additionally to the heap and so sometimes speed can be increased when you lower the `Xmx` value.

If you want to support none-CH requests you should at least enable landmarks or limit the request to a
certain distance otherwise it might require lots of RAM per request! See [#734](https://github.com/graphhopper/graphhopper/issues/734).

### API Tokens

By default, the GraphHopper UI uses [Omniscale](http://omniscale.com/) and/or [Thunderforest](http://thunderforest.com/) as layer service.
Either you get a plan there, then set the API key in the options.js file, or you
have to remove Omniscale from the [JavaScript file](https://github.com/graphhopper/graphhopper/blob/master/web/src/main/resources/com/graphhopper/maps/js/map.js).

GraphHopper uses the [GraphHopper Directions API](https://docs.graphhopper.com/#tag/Geocoding-API) for geocoding.
To be able to use the autocomplete feature of the point inputs you have to:

 * Get your API Token at: https://www.graphhopper.com/ and set this in the options.js
 * Don't forget the Attribution when using the free package

## Worldwide Setup

GraphHopper is able to handle coverage for the world-wide [OpenStreetMap road network](http://planet.osm.org/).

Without enabled CH the import step alone requires ~60GB RAM and takes ~3h for the import. If you can accept
much slower import times (3 days!) this can be reduced to 31GB RAM when you set `datareader.dataaccess=MMAP` in the config file.

With enabled CH for car (with turn cost) the import command needs ~120GB RAM and the additional CH preparation takes ~5 hours
but heavily depends on the CPU and memory speed.

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
 
