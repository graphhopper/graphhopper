## Map Matching based on GraphHopper

[![Build Status](https://secure.travis-ci.org/graphhopper/map-matching.png?branch=master)](http://travis-ci.org/graphhopper/map-matching)

Map matching is the process to match a sequence of real world coordinates into a digital map.
Read more at [Wikipedia](https://en.wikipedia.org/wiki/Map_matching). It can be used for tracking vehicles' GPS information, important for further digital analysis. Or e.g. attaching turn instructions for any recorded GPX route.

Currently this project is under heavy development but produces already good results for various use cases. Let us know if not and create an issue!

![Map Matching Illustration](https://karussell.files.wordpress.com/2014/07/map-matching.png)

### License

Apache License 2.0

### Installation and Usage

Install Java and Maven. Then import the area you want to do map-matching on:

```bash
./map-matching.sh action=import datasource=./some-dir/osm-file.pbf [vehicle=car]
```

and then do these matches:
```bash
./map-matching.sh action=match gpx=./track-data/.*gpx
```

Possible arguments are:
```bash
gpxAccuracy=15              # default=15, type=integer, unit=meter
separatedSearchDistance=500 # default=500, type=integer, unit=meter
maxSearchMultiplier=50      # default=50, type=integer
forceRepair=false           # default=false, type=boolean
```

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

with this maven dependency:

```xml
<dependency>
    <groupId>com.graphhopper</groupId>
    <artifactId>map-matching</artifactId>
    <!-- or 0.5-SNAPSHOT for the unstable -->
    <version>0.4.0</version>
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
