## Map Matching based on GraphHopper

Map matching is the process to match a sequence of real world coordinates into a digital map.
Read more at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching)

### License

Apache License 2.0

### Usage

Use this Java snippet:

```java
// import OpenStreetMap data
GraphHopper hopper = new GraphHopper();
hopper.setOSMFile("./map-data/leipzig_germany.osm.pbf");
hopper.setGraphHopperLocation("./target/mapmatchingtest");
CarFlagEncoder encoder = new CarFlagEncoder();
hopper.setEncodingManager(new EncodingManager(encoder));
hopper.setCHEnable(false);
hopper.importOrLoad();

// create MapMatching object, can and should be shared accross threads

Graph graph = hopper.getGraph();
LocationIndexMatch locationIndex = new LocationIndexMatch(graph, new RAMDirectory());
locationIndex.prepareIndex();
MapMatching mapMatching = new MapMatching(graph, locationIndex, encoder);

// do the actual matching, get the GPX entries from a file or via stream
List<GPXEntry> inputGPXEntries = new GPXFile("nice.gpx").read();
MatchResult mr = mapMatching.doWork(inputGPXEntries);

// return GraphHopper edges with all associated GPX entries
List<EdgeMatch> matches = mr.getEdgeMatches();
// now do something with the edges like storing the edgeIds or doing fetchWayGeometry etc
matches.get(0).edgeState
```

Later we will add a simple web service

### Note

Note that the edge and node IDs from GraphHopper will change for different PBF files,
like if updating the OSM data.