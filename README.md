## Map Matching based on GraphHopper

Map matching is the process to match a sequence of real world coordinates into a digital map.
Read more at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching)

### License

Apache License 2.0

### Installation

Install Java and Maven

### Usage

Import the area you want to do some route matches on, then do those matches:

 1. ./map-matching.sh action=import datasource=./some-dir/osm-file.pbf
 2. ./map-matching.sh action=match gpx=./track-data/.*gpx

This will produce gpx results similar named as the input files.

Or use this Java snippet:

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

### UI to visually compare

There is a simple UI taken from [makinacorpus/Leaflet.FileLayer](https://github.com/makinacorpus/Leaflet.FileLayer)
where you can load your input and output gpx files to compare the results. Some GPX seem to fail when trying to load them.

Start e.g. via 'firefox simple-js-ui/index.html'

### Note

Note that the edge and node IDs from GraphHopper will change for different PBF files,
like if updating the OSM data.