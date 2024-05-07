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
`edgeIteratorState.fetchWayGeometry(FetchMode.PILLAR_ONLY)`. Avoiding the traversal of pillar nodes makes 
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

### Handle graph and routing algorithms

See [this code](../../example/src/main/java/com/graphhopper/example/LowLevelAPIExample.java) for more details about using the low level API.
