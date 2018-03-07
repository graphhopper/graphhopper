# Isochrone via Java API

To use the following examples you need to specify the dependency in
your [Maven config](/README.md#maven) correctly.

To create an isochrone in Java code:

You'll first need to build off an existing Graphhopper instance for [routing](/../core/routing.md).

```java
// create one GraphHopper instance
GraphHopper hopper = new GraphHopperOSM().forServer();
hopper.setDataReaderFile(osmFile);
// where to store graphhopper files?
hopper.setGraphHopperLocation(graphFolder);
hopper.setEncodingManager(new EncodingManager("car"));

// now this can take minutes if it imports or a few seconds for loading
// of course this is dependent on the area you import
hopper.importOrLoad();
```

Next, you'll need to set a range of variable options for isochrone generation.
```java
String vehicle = "car";
int buckets = 1L;
boolean reverseFlow = false;
String queryStr = "point";
String resultStr = "polygon";
double distanceInMeter = 10000;
long timeLimitInSeconds = 600L;
// bigger raster distance => bigger raster => less points => stranger buffer results, but faster
double rasterDistance = 0.75;
// bigger buffer distance => less holes, lower means less points!
double bufferDistance = 0.003;
// precision of the 'circles'
int quadrantSegments = 3;

```

Next, compute the isochrone itself.
```java
// parse the query point and get an encoder

GHPoint query = GHPoint.parse(queryStr);

EncodingManager encodingManager = hopper.getEncodingManager();
if (!encodingManager.supports(vehicle)) {
    throw new IllegalArgumentException("vehicle not supported:" + vehicle);
}

FlagEncoder encoder = encodingManager.getEncoder(vehicle);
EdgeFilter edgeFilter = new DefaultEdgeFilter(encoder);

// pick the closest point on the graph to the query point
QueryResult qr = hopper.getLocationIndex().findClosest(query.lat, query.lon, edgeFilter);
if (!qr.isValid()) {
    throw new IllegalArgumentException("Point not found:" + query);
}

Graph graph = hopper.getGraphHopperStorage();
QueryGraph queryGraph = new QueryGraph(graph);
queryGraph.lookup(Collections.singletonList(qr));

HintsMap hintsMap = new HintsMap();
initHints(hintsMap, hReq.getParameterMap());
Weighting weighting = hopper.createWeighting(hintsMap, encoder, graph);
Isochrone isochrone = new Isochrone(queryGraph, weighting, reverseFlow);
if (distanceInMeter > 0) {
    double maxMeter = 50 * 1000;
    if (distanceInMeter > maxMeter)
        throw new IllegalArgumentException("Specify a limit of less than " + maxMeter / 1000f + "km");
    if (buckets > (distanceInMeter / 500))
        throw new IllegalArgumentException("Specify buckets less than the number of explored kilometers");
    isochrone.setDistanceLimit(distanceInMeter);
} else {
    long maxSeconds = 80 * 60;
    if (timeLimitInSeconds > maxSeconds)
        throw new IllegalArgumentException("Specify a limit of less than " + maxSeconds + " seconds");
    if (buckets > (timeLimitInSeconds / 60))
        throw new IllegalArgumentException("Specify buckets less than the number of explored minutes");

    isochrone.setTimeLimit(timeLimitInSeconds);
}

List<List<Double[]>> list = isochrone.searchGPS(qr.getClosestNode(), buckets);
if (isochrone.getVisitedNodes() > hopper.getMaxVisitedNodes() / 5) {
    throw new IllegalArgumentException("Reset: too many junction nodes would have to explored (" + isochrone.getVisitedNodes() + "). You may need to increase this value.");
}

int counter = 0;
for (List<Double[]> tmp : list) {
    if (tmp.size() < 2) {
        throw new IllegalArgumentException("Too few points found for bucket " + counter + ". "
                + "Please try a different point, a smaller buckets count, or a larger time limit.. ");
    }
    counter++;
}
```

Finally, convert the isochrone into a result, for easy return. You can either return a point list or a polygon.
```java
Object calcRes;

// get result as a point list
if ("pointlist".equalsIgnoreCase(resultStr)) {
    calcRes = list;

// or get result as a polygon
} else if ("polygon".equalsIgnoreCase(resultStr)) {
    list = rasterHullBuilder.calcList(list, list.size() - 1, rasterDistance, bufferDistance, quadrantSegments);

    ArrayList polyList = new ArrayList();
    int index = 0;
    for (List<Double[]> polygon : list) {
        HashMap<String, Object> geoJsonMap = new HashMap<>();
        HashMap<String, Object> propMap = new HashMap<>();
        HashMap<String, Object> geometryMap = new HashMap<>();
        polyList.add(geoJsonMap);
        geoJsonMap.put("type", "Feature");
        geoJsonMap.put("properties", propMap);
        geoJsonMap.put("geometry", geometryMap);

        propMap.put("bucket", index);
        geometryMap.put("type", "Polygon");
        // we have no holes => embed in yet another list
        geometryMap.put("coordinates", Collections.singletonList(polygon));
        index++;
    }
    calcRes = polyList;
} else {
    throw new IllegalArgumentException("type not supported:" + resultStr);
}
```