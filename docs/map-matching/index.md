## Map Matching based on GraphHopper

This module will do 'snap to road' using the routing engine.
        
Map matching is the process to match a sequence of real world coordinates into a digital map.
Read more at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching). It can be used for tracking vehicles' GPS information, important for further digital analysis. Or e.g. attaching turn instructions for any recorded GPX route.

Currently this project is under heavy development but produces already good results for various use cases. Let us know if not and create an issue!

See the demo in action (black is GPS track, green is matched result):

![map-matching-example](https://cloud.githubusercontent.com/assets/129644/14740686/188a181e-0891-11e6-820c-3bd0a975f8a5.png)

### License

Apache License 2.0

### Discussion

Discussion happens [here](https://discuss.graphhopper.com/c/graphhopper/map-matching).

### Installation and Usage

Java 8 and Maven >=3.3 are required.

Then you need to import the area you want to do map-matching on. For example:

```bash
git checkout [stable-branch]
./graphhopper.sh -a import -i /some-dir/osm-file.pbf -o graph-cache --profiles car
```

As an example you use `-i core/files/leipzig_germany.osm.pbf` as road network base or any other pbf
file from e.g. [here](http://download.geofabrik.de/).

The optional parameter `--profiles` defines the routing profile like `car`, `bike`, `motorcycle` or
`foot`. You can also provide a comma separated list. For all supported values see the variables in 
the [FlagEncoderFactory](https://github.com/graphhopper/graphhopper/blob/0.7/core/src/main/java/com/graphhopper/routing/util/FlagEncoderFactory.java) of GraphHopper. 

If you have already imported an input file with a specific vehicle profile, you need to remove the folder
graph-cache.

Now you can do these matches:
```bash
./graphhopper.sh -a map-matching-cli -i ./some-dir/*.gpx
```

As example use `-i ./map-matching/src/test/resources/*.gpx` or one specific gpx file.

Possible arguments are:
```bash
--instructions de             # default=, type=String, if an country-iso-code (like en or de) is specified turn instructions are included in the output, leave empty or default to avoid this
--gps_accuracy 15             # default=15, type=int, unit=meter, the precision of the used device
```

This will produce gpx results similar named as the input files.

Developer note: After changing the code you should run `mvn clean` before running
`graphhopper.sh` again.

### UI and matching Service

Start via:
```bash
./graphhopper.sh -a map-matching-web -o graph-cache
```

Access the simple UI via localhost:8989/map-matching

You can post GPX files and get back snapped results as GPX or as compatible GraphHopper JSON. An example curl request is:
```bash
curl -XPOST -H "Content-Type: application/gpx+xml" -d @/path/to/gpx/file.gpx "localhost:8989/match?vehicle=car&type=json"
```

#### Development tools

Determine the maximum bounds of one or more GPX file:
```bash
./graphhopper.sh -a map-matching-getbounds -i ./track-data/.*gpx
```

#### Java usage

Or use this Java snippet:

```java
// import OpenStreetMap data
GraphHopper hopper = new GraphHopperOSM();
hopper.setDataReaderFile("./core/files/leipzig_germany.osm.pbf");
hopper.setGraphHopperLocation("./target/mapmatchingtest");
CarFlagEncoder encoder = new CarFlagEncoder();
hopper.setEncodingManager(new EncodingManager(encoder));
hopper.getCHFactoryDecorator().setEnabled(false);
hopper.importOrLoad();

// create MapMatching object, can and should be shared accross threads
String algorithm = Parameters.Algorithms.DIJKSTRA_BI;
Weighting weighting = new FastestWeighting(encoder);
AlgorithmOptions algoOptions = new AlgorithmOptions(algorithm, weighting);
MapMatching mapMatching = new MapMatching(hopper, algoOptions);

// do the actual matching, get the GPX entries from a file or via stream
List<GPXEntry> inputGPXEntries = new GPXFile().doImport("nice.gpx").getEntries();
MatchResult mr = mapMatching.doWork(inputGPXEntries);

// return GraphHopper edges with all associated GPX entries
List<EdgeMatch> matches = mr.getEdgeMatches();
// now do something with the edges like storing the edgeIds or doing fetchWayGeometry etc
matches.get(0).getEdgeState();
```

with this maven dependency:

```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>graphhopper-map-matching-core</artifactId>
    <version>[latest version]</version>
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
