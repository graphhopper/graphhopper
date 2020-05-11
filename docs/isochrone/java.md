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

EdgeFilter edgeFilter = DefaultEdgeFilter.allEdges(encoder);
LocationIndex locationIndex = graphHopper.getLocationIndex();

// pick the closest point on the graph to the query point and generate a query graph
QueryResult qr = hopper.getLocationIndex().findClosest(lat, lon, DefaultEdgeFilter.allEdges(encoder));

Graph graph = hopper.getGraphHopperStorage();
QueryGraph queryGraph = QueryGraph.create(graph, qr);

// calculate isochrone from query graph
PMap pMap = new PMap();
Map<Integer,ShortestPathTree.IsoLabel> labelsMap = new HashMap<>();
ShortestPathTree spt = new ShortestPathTree(queryGraph, new FastestWeighting(carEncoder, new PMap()), false, TraversalMode.NODE_BASED);
spt.setTimeLimit(60);
spt.search(qr.getClosestNode(), label->labelsMap.put(label.node /*label.edge for edge-based traversal mode*/, label));
 
```
labelsMap will be a map of the traversal id and an isoLabel for all nodes (edges for edge-based traversal mode) reachable within the time limit.
For how to create a cirumscribed polygon for those reachable points,
and for a more comprehensive construction of an isochrone,
See [GraphHopper's servlet](https://github.com/graphhopper/graphhopper/blob/master/web-bundle/src/main/java/com/graphhopper/resources/IsochroneResource.java).
