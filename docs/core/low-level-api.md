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
`edgeIteratorState.fetchWayGeometry(FetchMode.PILLAR_ONLY)`. Avoiding the traversal of pillar nodes while routing makes 
routing a lot faster (~8 times).

That splitting into pillar and tower nodes is also the reason why there can't be a unique mapping from 
one OSM node ID to exactly one GraphHopper node ID. And as one OSM Way is often split into multiple 
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

A call `QueryGraph.create(graph, allQRs)` will determine the correct node for all `Snap`s: and either 
create new virtual nodes or if close enough use the existing junction node.

### Create and save the graph

```java
FlagEncoder encoder = new CarFlagEncoder();
EncodingManager em = EncodingManager.create(encoder);
GraphHopperStorage graph = new GraphBuilder(em).setRAM("graphhopper_folder", true).create();
// Make a weighted edge between two nodes.
EdgeIteratorState edge = graph.edge(fromId, toId);
edge.setDistance(distance);
edge.set(encoder.getAverageSpeedEnc(), 50);

// Set node coordinates and build location index
NodeAccess na = graph.getNodeAccess();
na.setNode(5, 15.15, 20.20);
na.setNode(10, 15.25, 20.21);
LocationIndexTree index = new LocationIndexTree(graph, graph.getDirectory());
index.prepareIndex();

// Flush the graph and location index to disk
graph.flush();
index.flush();
```

### Load the graph

```java
FlagEncoder encoder = new CarFlagEncoder();
EncodingManager em = EncodingManager.create(encoder);
GraphHopperStorage graph = new GraphBuilder(em).setRAM("graphhopper_folder", true).build();
graph.loadExisting();

// Load the location index
LocationIndex index = new LocationIndexTree(graph.getBaseGraph(), graph.getDirectory());
if (!index.loadExisting())
    throw new IllegalStateException("location index cannot be loaded!");
```

### Calculate Path with LocationIndex

```java
Snap fromSnap = index.findClosest(latitudeFrom, longituteFrom, EdgeFilter.ALL_EDGES);
Snap toSnap = index.findClosest(latitudeTo, longituteTo, EdgeFilter.ALL_EDGES);
QueryGraph queryGraph = QueryGraph.create(graph, fromSnap, toSnap);
Weighting weighting = new FastestWeighting(encoder);
Path path = new Dijkstra(queryGraph, weighting).calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());
```

### Calculate Path without LocationIndex

```java
// get the fromId and toId nodes from other code parts
Path path = new Dijkstra(graph, weighting).calcPath(fromId, toId);
```

### Use Contraction Hierarchies to make queries faster

```java
// Creating and saving the graph
FlagEncoder encoder = new CarFlagEncoder();
EncodingManager em = EncodingManager.create(encoder);
Weighting weighting = new FastestWeighting(encoder);
CHConfig chConfig = CHConfig.nodeBased("my_profile", weighting);
GraphHopperStorage graph = new GraphBuilder(em)
        .setRAM("graphhopper_folder", true)
         // need to setup CH at time of graph creation here!
        .setCHConfigs(chConfig)
        .create();

// ... (not shown) set edges and nodes/coordinates as above

// Prepare the graph for fast querying ...
PrepareContractionHierarchies pch = PrepareContractionHierarchies.fromGraphHopperStorage(graph, chConfig);
pch.doWork();

// flush after preparation!
graph.flush();

// get the CH graph
RoutingCHGraph chGraph = graph.getRoutingCHGraph("my_profile");

// calculate a path without location index
BidirRoutingAlgorithm algo = new CHRoutingAlgorithmFactory(chGraph).createAlgo(new PMap());
algo.calcPath(fromId, toId);

// calculate a path with location index
QueryGraph queryGraph = QueryGraph.create(graph, fromSnap, toSnap); // use index as shown above
BidirRoutingAlgorithm algo = new CHRoutingAlgorithmFactory(chGraph, queryGraph).createAlgo(new PMap());
algo.calcPath(fromSnap.getClosestNode(), toSnap.getClosestNode());
```

**See GraphHopper's many unit tests for up-to date and more detailed usage examples of the low level API!**
