# Isochrone via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

You'll first need to build off an existing Graphhopper instance for [routing](../core/routing.md).

See this [example code](../../example/src/main/java/com/graphhopper/example/IsochroneExample.java)
how to build and traverse a shortest path tree, which means enumerating, in order, all nodes that 
can be reached within a given time limit.

See [IsochroneResource.java](../../web-bundle/src/main/java/com/graphhopper/resources/IsochroneResource.java)
to see how we use the shortest path tree to construct an isochrone (or other isoline, depending on the weighting).