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

