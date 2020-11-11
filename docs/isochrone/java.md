# Isochrone via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

To create an isochrone in Java code:

You'll first need to build off an existing Graphhopper instance for [routing](/../core/routing.md).

Next, compute the isochrone itself.

See the [example code](../../example/src/main/java/com/graphhopper/example/IsochroneExample.java).

See [IsochroneResource.java](https://github.com/graphhopper/graphhopper/blob/master/web-bundle/src/main/java/com/graphhopper/resources/IsochroneResource.java)
to find out how to create an iso line polygon ("isochrone") from using the shortest path tree.