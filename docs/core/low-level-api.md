## Low level API

If you just start to use GraphHopper please refer to [routing docs](./routing.md)
or [the quickstart for developers](./quickstart-from-source.md)
and come back here later if the higher level API does not suit your needs.

### What are pillar and tower nodes?

From road network sources like OpenStreetMap we fetch all nodes and create the routing graph but 
only a sub-set of them are actual junctions, which are the ones we are interested in while routing.

Those junction nodes (and end-standing nodes of dead alleys) we call *tower nodes* which also 
have a graphhopper node ID associated, going from 0 to graph.getNodes(). 
The helper nodes between the junctions we call 'pillar nodes' which can be fetched via
`edgeIteratorState.fetchWayGeometry(0)`. Avoiding the traversal of pillar nodes while routing makes 
routing a lot faster (~8 times).

That splitting into pillar and tower nodes is also the reason why there can't be a unique mapping from 
one OSM node ID to exactly one GraphHopper node ID. And as one OSM Way is often splitted into multiple 
edges the same applies for edge IDs too.

### What are virtual edges and nodes?

For a route you do not only need *junction-precision*, i.e. from tower node to tower node, but we want 
*GPS-precise* routes, otherwise [you'll get lots of trouble](https://github.com/graphhopper/graphhopper/issues/27) 
for oneways and similar.

To make GPS precise routes possible, although we route from tower node to tower node, we introduce one new 
virtual node x and virtual edges A-x, x-B for every query point located on an edge A-B:

```bash
\                /
 A---x---------B
/                \
```

But we need to decouple requests from each other and therefor we create a very lightweight graph called 
`QueryGraph` for every request which handles also stuff like two query points on the same edge.

The virtual nodes and edges have a higher `int` ID than `graph.getNodes()` or `allEdges.length()`

A call `queryGraph.lookup(allQRs)` will determine the correct node for all `QueryResult`s: and either 
create new virtual nodes or if close enough use the existing junction node.

### Create and save the graph

```java
FlagEncoder encoder = new CarFlagEncoder();
EncodingManager em = new EncodingManager(encoder);
GraphBuilder gb = new GraphBuilder(em).setLocation("graphhopper_folder").setStore(true);
GraphStorage graph = gb.create();
// Make a weighted edge between two nodes.
EdgeIteratorState edge = graph.edge(fromId, toId);
edge.setDistance(distance);
edge.setFlags(encoder.setProperties(speed, true, true));
// Flush to disc
graph.flush();
```

### Load the graph

```java
...
GraphStorage graph = gb.load();
// Load index
LocationIndex index = new LocationIndexTree(graph.getBaseGraph(), new RAMDirectory("graphhopper_folder", true));
if (!index.loadExisting())
    throw new IllegalStateException("location index cannot be loaded!");
```

### Calculate Path with LocationIndex

```java
QueryResult fromQR = index.findClosest(latitudeFrom, longituteFrom, EdgeFilter.ALL_EDGES);
QueryResult toQR = index.findClosest(latitudeTo, longituteTo, EdgeFilter.ALL_EDGES);
QueryGraph queryGraph = new QueryGraph(graph);
queryGraph.lookup(fromQR, toQR);
Path path = new Dijkstra(queryGraph, encoder).calcPath(fromQR.getClosestNode(), toQR.getClosestNode());
```

### Calculate Path without LocationIndex

```java
// get the fromId and toId nodes from other code parts
Path path = new Dijkstra(graph, encoder).calcPath(fromId, toId);
```

### Use CHGraph to make queries faster

```java
// Creating and saving the graph
GraphBuilder gb = new GraphBuilder(em).
    setLocation("graphhopper_folder").
    setStore(true).
    setCHGraph(true);
GraphHopperStorage graph = gb.create();
// Create a new edge between two nodes, set access, distance, speed, geometry, ..
EdgeIteratorState edge = graph.edge(fromId, toId);
...

// Prepare the graph for fast querying ...
TraversalMode tMode = TraversalMode.NODE_BASED;
PrepareContractionHierarchies pch = new PrepareContractionHierarchies(ghStorage, encoder, weighting, tMode);
pch.doWork();

// flush after preparation!
graph.flush();

// Load and use the graph
GraphStorage graph = gb.load();

 // Load index
LocationIndex index = new LocationIndexTree(graph.getBaseGraph(), new RAMDirectory("graphhopper_folder", true));
if (!index.loadExisting())
    throw new IllegalStateException("location index cannot be loaded!");

// calculate path is identical
QueryResult fromQR = index.findClosest(latitudeFrom, longituteFrom, EdgeFilter.ALL_EDGES);
QueryResult toQR = index.findClosest(latitudeTo, longituteTo, EdgeFilter.ALL_EDGES);
QueryGraph queryGraph = new QueryGraph(graph);
queryGraph.lookup(fromQR, toQR);

// create the algorithm using the PrepareContractionHierarchies object
AlgorithmOptions algoOpts = AlgorithmOptions.start().
   algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(tMode).weighting(weighting).
   build();
RoutingAlgorithm algorithm = pch.createAlgo(queryGraph, algoOpts);
Path path = algorithm.calcPath(fromQR.getClosestNode(), toQR.getClosestNode());
```
