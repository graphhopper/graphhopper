# Deploy Guide

This guide is written for everyone interested in deploying graphhopper on a server.

## Basics

See the [installation section](../../README.md#installation) on how to start the server.

Then you can embed these commands in a shell script and use this from e.g. [Docker](../../README.md#docker) or systemd.

For production usage you have a web service included where you can use [this configuration](https://raw.githubusercontent.com/graphhopper/graphhopper/master/config-example.yml)
Increase the -Xmx/-Xms parameters of the command accordingly.

You can reduce the memory requirements for the import step when you run the `import` command explicitly before the `server` command:

```
java [options] -jar *.jar import config.yml
java [options] -jar *.jar server config.yml # calls the import command implicitly, if not done before
```

To further reduce memory usage for `import` try a special garbage collector (GC): `-XX:+UseParallelGC`.

However after the import, for serving the routing requests GCs like ZGC or Shenandoah could be better than the default G1 as those are optimized for JVMs with bigger heaps (>32GB) and low pauses.
They can be enabled with `-XX:+UseZGC` or `-XX:+UseShenandoahGC`. Please note that especially ZGC and G1 require quite a
bit memory additionally to the heap and so sometimes overall speed could be increased when lowering the `Xmx` value.

If you want to support none-CH requests you should consider enabling landmarks or limit requests to a
certain distance via `routing.non_ch.max_waypoint_distance` (in meter, default is 1) or
to a node count via `routing.max_visited_nodes`.
Otherwise it might require lots of RAM per request! See [#734](https://github.com/graphhopper/graphhopper/issues/734).

### API Tokens

The GraphHopper Maps UI uses the [GraphHopper Directions API](https://docs.graphhopper.com/#tag/Geocoding-API) for geocoding.
To be able to use the autocomplete feature of the point inputs you get your API Token at
[graphhopper.com](https://www.graphhopper.com/) and set this in the config.js file, see
web-bundle/src/main/resources/com/graphhopper/maps/config.js

The Maps UI also uses [Omniscale](http://omniscale.com/) and [Thunderforest](http://thunderforest.com/) as layer service.
You can get a plan there too and set the API keys in the config.js file.


## Worldwide Setup

GraphHopper can handle the world-wide [OpenStreetMap road network](http://planet.osm.org/).

Parsing this planet file and creating the GraphHopper base graph requires ~60GB RAM and takes ~3h for the import. If you can accept
much slower import times (3 days!) this can be reduced to 31GB RAM when you set `datareader.dataaccess=MMAP` in the config file.
As of May 2022 the graph has around 415M edges (150M for Europe, 86M for North America).

Running the CH preparation, required for best response times, needs ~120GB RAM and the additional CH preparation takes ~25 hours
(for the car profile with turn cost) but heavily depends on the CPU and memory speed. Without turn cost
support, e.g. sufficient for bike, it takes much less (~5 hours).

Running the LM preparation for the car profile needs ~80GB RAM and
the additional LM preparation takes ~4 hours.

It is also possible to add CH/LM preparations for existing profiles after the initial import.
Adding or modifying profiles is not possible and you need to run a new import instead.

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
 
