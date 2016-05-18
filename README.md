## Map Matching based on GraphHopper

[![Build Status](https://secure.travis-ci.org/graphhopper/map-matching.png?branch=master)](http://travis-ci.org/graphhopper/map-matching)

Map matching is the process to match a sequence of real world coordinates into a digital map.
Read more at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching). It can be used for tracking vehicles' GPS information, important for further digital analysis. Or e.g. attaching turn instructions for any recorded GPX route.

Currently this project is under heavy development but produces already good results for various use cases. Let us know if not and create an issue!

![Map Matching Illustration](https://karussell.files.wordpress.com/2014/07/map-matching.png)

### License

Apache License 2.0

### Discussion

Discussion happens [here](https://discuss.graphhopper.com/c/graphhopper/map-matching).

### Installation and Usage

Java 8 and Maven >=3.3 are required. For the 'core' module Java 7 is sufficient.

Then you need to import the area you want to do map-matching on:

```bash
./map-matching.sh action=import datasource=./some-dir/osm-file.pbf [vehicle=car]
```

The parameter vehicle defines the routing profile like `bike`, `motorcycle` or `foot`. 
For all supported values see the variables in the EncodingManager class of GraphHopper. 

If you have already imported a datasource with a specific profile, you need to remove the folder graph-cache in your map-matching root directory.

Now you can do these matches:
```bash
./map-matching.sh action=match gpx=./track-data/.*gpx
```

Possible arguments are:
```bash
instructions=de             # default=, type=String, if an country-iso-code (like en or de) is specified turn instructions are included in the output, leave empty or default to avoid this
gpx_accuracy=15              # default=15, type=int, unit=meter, the precision of the used device
separated_search_distance=500 # default=500, type=int, unit=meter, we split the incoming list into smaller parts (hopefully) without loops. Later we'll detect loops and insert the correctly detected road recursivly, see #1
max_visited_nodes=1000        # default=1000, type=int, the limit we use to search a route from one gps entry to the other to avoid exploring the whole graph in case of disconnected subnetworks.
force_repair=false           # default=false, type=boolean, when merging two path segments it can happen that edges seem illegal like two adjacent and parallel edges and the search will normally fail. Setting this to true tries to clean the illegal situation
```

This will produce gpx results similar named as the input files.

### Matching Service

Start via:
```bash
./map-matching.sh action=start-server
```

Now you can post GPX files and get back snapped results as GPX or as compatible GraphHopper JSON. An example curl request is:
```bash
curl -XPOST -H "Content-Type: application/gpx+xml" -d @/path/to/gpx/file.gpx "localhost:8989/match?vehicle=car&max_nodes_to_visit=1000&force_repair=true&type=json"
```

Keep in mind that camelCase variables are converted to under_score variables like seen in the example (maxNodesToVisit=>max_nodes_to_visit).

#### Development tools

Determine the maximum bounds of one or more GPX file:
```bash
./map-matching.sh action=getbounds gpx=./track-data/.*gpx
```

#### Java usage

Or use this Java snippet:

```java
// import OpenStreetMap data
GraphHopper hopper = new GraphHopper();
hopper.setOSMFile("./map-data/leipzig_germany.osm.pbf");
hopper.setGraphHopperLocation("./target/mapmatchingtest");
CarFlagEncoder encoder = new CarFlagEncoder();
hopper.setEncodingManager(new EncodingManager(encoder));
hopper.getCHFactoryDecorator().setEnabled(false);
hopper.importOrLoad();

// create MapMatching object, can and should be shared accross threads

GraphHopperStorage graph = hopper.getGraphHopperStorage();
LocationIndexMatch locationIndex = new LocationIndexMatch(graph,
                (LocationIndexTree) hopper.getLocationIndex());
MapMatching mapMatching = new MapMatching(graph, locationIndex, encoder);

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
    <artifactId>map-matching</artifactId>
    <!-- or 0.7-SNAPSHOT for the unstable -->
    <version>0.6.0</version>
</dependency>
```

Later we will add a simple web service

### UI to visually compare

There is a simple UI taken from [makinacorpus/Leaflet.FileLayer](https://github.com/makinacorpus/Leaflet.FileLayer)
where you can load your input and output gpx files to compare the results. Some GPX seem to fail when trying to load them.

Start e.g. via 'firefox simple-js-ui/index.html'

### Note

Note that the edge and node IDs from GraphHopper will change for different PBF files,
like when updating the OSM data.

### About

The used algorithm is explained in [this blog post](http://karussell.wordpress.com/2014/07/28/digitalizing-gpx-points-or-how-to-track-vehicles-with-graphhopper/).
