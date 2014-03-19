## Low level API

If you just start to use GraphHopper please refer to routing.md and come back here later if the
higher level API does not suit your needs.

### Create and save the graph

```java
FlagEncoder encoder = new CarFlagEncoder();
EncodingManager em = new EncodingManager(encoder);
GraphBuilder gb = new GraphBuilder(em).setLocation("graphhopper-folder").setStore(true);
GraphStorage graph = gb.create();
// Make a weighted edge between two nodes.
EdgeIteratorState edge = graph.edge(fromId, toId);
edge.setDistance(distance);
edge.setFlags(encoder.setProperties(speed, true, true));
// Store to disc
graph.flush();
```

### Load the graph

```java
...
GraphStorage graph = gb.load();
// Load index
LocationIndex index = new LocationIndexTree(graph, new RAMDirectory("graphhopper-folder", true));
if (!index.loadExisting())
    throw new IllegalStateException("location index cannot be loaded!");
```

### Calculate Path with LocationIndex
QueryResult fromQR = index.findClosest(latitudeFrom, longituteFrom, EdgeFilter.ALL_EDGES);
QueryResult toQR = index.findID(latitudeTo, longituteTo, EdgeFilter.ALL_EDGES);
Path path = new Dijkstra(graph, encoder).calcPath(fromQR, toQR);
```

### Calculate Path without LocationIndex

```java
// get the fromId and toId nodes from other code parts
Path path = new Dijkstra(graph, encoder).calcPath(fromId, toId);
```

### Use LevelGraph to make queries faster

```java
// Creating and saving the graph
GraphBuilder gb = new GraphBuilder(em).
    setLocation("graphhopper-folder").
    setStore(true).
    setLevelGraph(true);
GraphStorage graph = gb.create();
// Make a weighted edge between two nodes.
EdgeIteratorState edge = graph.edge(fromId, toId);
...

// Prepare the graph for fast querying ...
PrepareContractionHierarchies pch = new PrepareContractionHierarchies();
pch.setGraph(graph).doWork();

// flush after preparation!
graph.flush();

// Load and use the graph
GraphStorage graph = gb.load();

 // Load index
Location2IDIndex index = new LocationIndexTreeSC(graph, new RAMDirectory("graphhopper-folder", true));
if (!index.loadExisting())
    throw new IllegalStateException("location2id index cannot be loaded!");

// create the algorithm using the PrepareContractionHierarchies object
RoutingAlgorithm algorithm = pch.createAlgo();

// calculate path is identical
QueryResult fromQR = index.findClosest(latitudeFrom, longituteFrom, EdgeFilter.ALL_EDGES);
QueryResult toQR = index.findID(latitudeTo, longituteTo, EdgeFilter.ALL_EDGES);
Path path = new Dijkstra(graph, encoder).calcPath(fromQR, toQR);
```