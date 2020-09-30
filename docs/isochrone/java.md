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

// snap some GPS coordinates to the routing graph and build a query graph
Snap snap = hopper.getLocationIndex().findClosest(lat, lon, DefaultEdgeFilter.allEdges(encoder));
QueryGraph queryGraph = QueryGraph.create(hopper.getGraphHopperStorage(), snap);

// run the isochrone calculation
ShortestPathTree tree = new ShortestPathTree(queryGraph, new FastestWeighting(encoder), false, TraversalMode.NODE_BASED);
// find all nodes that are within a radius of 60s
tree.setTimeLimit(60_000);
// you need to specify a callback to define what should be done 
tree.search(snap.getClosestNode(),  label -> {
    // see IsoLabel.java for more properties
    System.out.println("node: " + label.node);
    System.out.println("time: " + label.time);
    System.out.println("distance: " + label.distance);
});
```

See [IsochroneResource.java](https://github.com/graphhopper/graphhopper/blob/master/web-bundle/src/main/java/com/graphhopper/resources/IsochroneResource.java)
to find out how to create an iso line polygon / isochrone from using the shortest path tree.
