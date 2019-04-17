## Map Matching based on GraphHopper

[![Build Status](https://secure.travis-ci.org/graphhopper/map-matching.png?branch=master)](http://travis-ci.org/graphhopper/map-matching)

Snaps GPX traces to the road using the
[GraphHopper routing engine](https://github.com/graphhopper/graphhopper). 
        
Read more about the map matching problem at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching). 

Currently this project is under development but produces already good results for various use cases. Let us know how it works for you!

See the demo in action (black is GPS track, green is matched result):

![map-matching-example](https://cloud.githubusercontent.com/assets/129644/14740686/188a181e-0891-11e6-820c-3bd0a975f8a5.png)

### License

Apache License 2.0

### Discussion

Our web forum is [here](https://discuss.graphhopper.com/c/graphhopper/map-matching).

### Usage

Java 8 and Maven >=3.3 are required. For the 'core' module Java 7 is sufficient.

Build:

```bash
mvn package -DskipTests
```

Then you need to import an OSM map for the area you want to do map-matching on, e.g. the provided
sample data:

```bash
java -jar matching-web/target/graphhopper-map-matching-web-0.13-SNAPSHOT.jar import map-data/leipzig_germany.osm.pbf
```

OpenStreetMap data in pbf or xml format are available from [here](http://download.geofabrik.de/).

The optional parameter `--vehicle` defines the routing profile like `car`, `bike`, `motorcycle` or `foot`.
You can also provide a comma separated list. For all supported values see the variables in the [FlagEncoderFactory](https://github.com/graphhopper/graphhopper/blob/0.7/core/src/main/java/com/graphhopper/routing/util/FlagEncoderFactory.java) of GraphHopper. 

Before re-importing, you need to delete the `graph-cache` directory, which is created by the import.

Now you can match GPX traces against the map:
```bash
java -jar matching-web/target/graphhopper-map-matching-web-0.13-SNAPSHOT.jar match matching-core/src/test/resources/*.gpx
```

### Web app

Start via:
```bash
java -jar matching-web/target/graphhopper-map-matching-web-0.13-SNAPSHOT.jar server config.yml
```

Access the simple UI via `localhost:8989`.

You can post GPX files and get back snapped results as GPX or as compatible GraphHopper JSON. An example curl request is:
```bash
curl -XPOST -H "Content-Type: application/gpx+xml" -d @matching-core/src/test/resources/test1.gpx "localhost:8989/match?vehicle=car&type=json"
```

#### Tools

Determine the bounding box of one or more GPX files:
```bash
java -jar matching-web/target/graphhopper-map-matching-web-0.13-SNAPSHOT.jar getbounds matching-core/src/test/resources/*.gpx
```

#### Java usage

Have a look at `MapMatchingResource.java` to see how the web service is implemented on top
of library functions to get an idea how to use map matching in your own project.

Use this Maven dependency:
```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-map-matching-core</artifactId>
    <version>0.13-SNAPSHOT</version>
</dependency>
```

### Note

Note that the edge and node IDs from GraphHopper will change for different PBF files,
like when updating the OSM data.

### About

The map matching algorithm mainly follows the approach described in

*Newson, Paul, and John Krumm. "Hidden Markov map matching through noise and sparseness."
Proceedings of the 17th ACM SIGSPATIAL International Conference on Advances in Geographic
Information Systems. ACM, 2009.*

This algorithm works as follows. For each input GPS position, a number of
map matching candidates within a certain radius around the GPS position is computed.
The [Viterbi algorithm](https://en.wikipedia.org/wiki/Viterbi_algorithm) as provided by the
[hmm-lib](https://github.com/bmwcarit/hmm-lib) is then used to compute the most likely sequence
of map matching candidates. Thereby, the distances between GPS positions and map matching
candidates as well as the routing distances between consecutive map matching candidates are taken
into account. The GraphHopper routing engine is used to find candidates and to compute routing
distances.

Before GraphHopper 0.8, [this faster but more heuristic approach](https://karussell.wordpress.com/2014/07/28/digitalizing-gpx-points-or-how-to-track-vehicles-with-graphhopper/)
was used.
