# Isochrone via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

To create an isochrone in Java code:

You'll first need to build off an existing Graphhopper instance for [routing](/../core/routing.md).

Next, compute the isochrone itself.
```java

// get encoder from GraphHopper instance
EncodingManager encodingManager = hopper.getEncodingManager();
FlagEncoder encoder = encodingManager.getEncoder("car");

// pick the closest point on the graph to the query point and generate a query graph
QueryResult qr = hopper.getLocationIndex().findClosest(lat, lon, DefaultEdgeFilter.allEdges(encoder));

Graph graph = hopper.getGraphHopperStorage();
QueryGraph queryGraph = new QueryGraph(graph);
queryGraph.lookup(Collections.singletonList(qr));

// calculate isochrone from query graph
PMap pMap = new PMap();
Isochrone isochrone = new Isochrone(queryGraph, new FastestWeighting(carEncoder, pmap), false);
isochrone.setTimeLimit(60);

List<List<Double[]>> res = isochrone.searchGPS(qr.getClosestNode(), 1L);
```

The returned list will represent a point list. It can also be converted into a polygon.

See [GraphHopper's servlet](https://github.com/graphhopper/graphhopper/blob/master/web-bundle/src/main/java/com/graphhopper/resources/IsochroneResource.java)
for more comprehensive construction of an isochrone.
