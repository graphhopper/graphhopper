# Location Index

You get the location index from the GraphHopper instance, after you imported or loaded the data:

```java
GraphHopper hopper = new GraphHopper();
hopper.set...
hopper.importOrLoad();

LocationIndex index = hopper.getLocationIndex();

//  now you can fetch the closest edge via:
Snap snap = index.findClosest(lat, lon, EdgeFilter.ALL_EDGES );
EdgeIteratorState edge = snap.getClosestEdge();
```

If you don't use the GraphHopper class you have to use the low level API:

```java
LocationIndexTree index = new LocationIndexTree(graph.getBaseGraph(), dir);
index.setResolution(preciseIndexResolution);
index.setMaxRegionSearch(maxRegionSearch);
if (!index.loadExisting())
    index.prepareIndex();
```