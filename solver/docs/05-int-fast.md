# solver-int — Int-based specialization (future)

**Status: planned — not implemented in Phase 1.**

The `solver-int` module is a specialized version of the solver for large graphs
where performance is critical. It eliminates boxing, GC pressure,
and the overhead of generic collections.

---

## Motivation

The generic model `<N, E, W>` is clean and readable, but on graphs
with millions of nodes (e.g., Europe's road network) it incurs costs:

| Problem | Cause | Cost |
|---------|-------|------|
| Boxing int → Integer | Nodes in `Map<N, W>` | ~16 bytes/entry overhead |
| Boxing double → Double | Weights in the heap | GC pressure |
| HashMap overhead | `Map<N, W>` dist table | ~50 bytes/entry |
| Object headers | Each `Edge<N,E>` record | 16 bytes/object |

For the Europe graph (~25M nodes, ~50M edges) this is the difference between ~2GB RAM
and ~400MB RAM. And a 2-3x difference in query time.

---

## Strategy: adapter, not rewrite

We do not rewrite algorithms from scratch. Instead:

1. **`IntGraph`** — specialized graph (int nodes, flat-array adjacency)
2. **`IntDijkstra`** — Dijkstra with `int[]` dist table instead of `Map<N, W>`
3. **Adapter** — `IntGraph` implements `Graph<Integer, E>` (optionally)
   or we convert `Graph<Integer, E>` → `IntGraph`

```
Graph<Integer, E>  ←──adapter──→  IntGraph
       ↓                              ↓
  Dijkstra<Integer,E,Double>    IntDijkstra (zero boxing)
```

---

## IntGraph — flat-array adjacency list

Inspired by `BaseGraphNodesAndEdges` from GraphHopper.

```java
/**
 * Graph with int nodes and flat-array storage.
 * Adjacency list as a linked list in an array.
 *
 * nodeData[node] = firstEdgeRef (pointer to edgeData)
 * edgeData[edge * EDGE_STRIDE + OFFSET_*] = ...
 */
public class IntGraph {

    /** Number of nodes. */
    int nodeCount();

    /** Iterates over outgoing edges from a node. */
    IntEdgeIterator neighbors(int node);

    /** Builder. */
    static IntGraph.Builder builder(int expectedNodes);
}

/**
 * Edge iterator — mutable, reusable, zero allocations.
 * Pattern from GraphHopper (EdgeIterator).
 */
public interface IntEdgeIterator {
    boolean next();
    int source();
    int target();
    int edgeId();
    // edge data read by IntCostFunction
}
```

### Storage layout

```
nodeData: [firstEdge_0, firstEdge_1, ..., firstEdge_V]

edgeData: [nodeA, nodeB, linkA, linkB, dist_mm, prop1, prop2, ...]
           |--- EDGE_STRIDE ---|

linkA = next edge ref for nodeA (linked list)
linkB = next edge ref for nodeB (linked list)
```

Cache-friendly: sequential array reads instead of random access in HashMap.

---

## IntDijkstra — zero-boxing Dijkstra

```java
/**
 * Dijkstra optimized for int nodes and double weights.
 * Uses int[] instead of Map<N,W> and IntMinHeap instead of MinHeap<T>.
 */
public class IntDijkstra {

    IntDijkstra(IntGraph graph, IntCostFunction costFunction);

    IntPath solve(int source, int target);
}

@FunctionalInterface
public interface IntCostFunction {
    double cost(IntEdgeIterator edge);
}
```

### Data structures

```java
double[] dist;           // dist[node] = best known distance
int[] prevEdge;          // prevEdge[node] = edge leading to node
IntMinHeap heap;         // binary heap on int[] (zero boxing)
```

---

## When to use

| Graph size | Recommendation |
|------------|----------------|
| < 10k nodes | Generic `Dijkstra<N,E,W>` — boxing is negligible |
| 10k - 100k nodes | Generic is OK, int-based gives ~2x speedup |
| > 100k nodes | Int-based strongly recommended |
| > 1M nodes | Int-based + CH/LM preprocessing |

**The decision should be benchmark-driven**, not assumed upfront.
The generic model is correct and sufficient for many use cases.

---

## Adapter: Graph\<Integer, E\> → IntGraph

```java
/**
 * Converts a generic graph with Integer nodes
 * to IntGraph (flat-array).
 *
 * Requires nodes to be contiguous: 0, 1, 2, ..., V-1.
 * If not — applies remapping.
 */
public class IntGraphAdapter {

    /**
     * Converts Graph<Integer, E> to IntGraph.
     * If nodes are not contiguous, creates a mapping.
     */
    public static <E> IntGraph fromGenericGraph(Graph<Integer, E> graph);

    /**
     * Creates a wrapper Graph<Integer, E> around IntGraph (lazy, zero-copy).
     */
    public static <E> Graph<Integer, E> toGenericGraph(IntGraph intGraph, ...);
}
```

---

## What to copy from GH

| Element | Source in GH | How to adapt |
|---------|-------------|--------------|
| Flat-array adjacency | `BaseGraphNodesAndEdges` | Simplify — without encoded values, without bit-packing |
| `MinHeapWithUpdate` | `coll/MinHeapWithUpdate` | Almost 1:1 — it's already int-based |
| Mutable EdgeIterator | `EdgeIterator` pattern | Keep the pattern, but without God Object (3 methods instead of 37) |

---

## Implementation plan

Not in Phase 1. Implementation when:
1. The generic model is ready and tested
2. Benchmarks show that boxing is the bottleneck
3. A real-world use case exists (e.g., routing on OSM)

Estimated size: ~500 lines of code (IntGraph + IntDijkstra + IntMinHeap + adapter).
