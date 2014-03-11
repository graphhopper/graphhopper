You get the location index from the GraphHopper instance, after you imported or loaded the data:

```java
GraphHopper hopper = new GraphHopper();
hopper.set...
hopper.importOrLoad();

LocationIndex index = hopper.getLocationIndex();

//  now you can fetch the closest edge via:
QueryResult qr = findClosest(lat, lon, EdgeFilter.ALL_EDGES );
EdgeIteratorState edge = qr.getClosestEdge();
```

If you don't use the GraphHopper class you have to handle every case on your own to build a location index.
E.g. if it is a LevelGraph you need LocationIndexTreeSC otherwise LocationIndexTree:

```java
LocationIndexTree tmpIndex;
if (graph instanceof LevelGraph)
   tmpIndex = new LocationIndexTreeSC((LevelGraph) graph, dir);
else
   tmpIndex = new LocationIndexTree(graph, dir);

tmpIndex.setResolution(preciseIndexResolution);
tmpIndex.setSearchRegion(searchRegion);
// now build the index if it cannot be loaded
if (!tmpIndex.loadExisting())
   tmpIndex.prepareIndex();
```