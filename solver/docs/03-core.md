# solver-core — Algorithms

The `solver-core` module contains shortest path algorithm implementations.
It depends **exclusively** on `solver-api`. It knows nothing about storage, data format,
or concrete graph types.

Package: `pl.cezarysanecki.solver.core`

---

## MinHeap\<T\> — Priority Queue with decreaseKey

Inspired by `MinHeapWithUpdate` from GraphHopper — a better PQ than `java.util.PriorityQueue`,
which does not support `decreaseKey` in O(log n).

```java
/**
 * Min-heap with O(log n) decreaseKey operation.
 * A key data structure for Dijkstra and A*.
 *
 * Unlike java.util.PriorityQueue:
 * - decreaseKey in O(log n) instead of O(n) (remove + add)
 * - contains in O(1)
 *
 * @param <T> element type
 */
public class MinHeap<T> {

    MinHeap(Comparator<T> comparator);

    /** Inserts an element. O(log n). */
    void insert(T element);

    /** Returns and removes the minimum. O(log n). */
    T extractMin();

    /** Notifies the heap that an element's priority has decreased. O(log n). */
    void decreaseKey(T element);

    /** Returns the minimum without removing it. O(1). */
    T peekMin();

    /** Does the heap contain the element? O(1). */
    boolean contains(T element);

    boolean isEmpty();
    int size();
}
```

### Implementation

Binary heap in an array with an additional `Map<T, Integer>` (element → index in array)
for O(1) lookup and O(log n) decreaseKey. Same strategy as in GH, but generic
instead of hardcoded to int node IDs.

**Note for the future:** In `solver-int` (int-based fast-path) this map will be
replaced with an int[] array — zero boxing, zero GC.

---

## Dijkstra\<N, E, W\>

Classic Dijkstra's algorithm — single-source shortest path.

```java
/**
 * Dijkstra's algorithm for finding the shortest path.
 *
 * Requirements:
 * - Non-negative weights (CostFunction returns w >= algebra.zero())
 * - WeightAlgebra defines a valid ordered monoid
 *
 * Complexity: O((V + E) log V) with MinHeap
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public class Dijkstra<N, E, W> implements ShortestPathSolver<N, E, W> {

    public Dijkstra(
        Graph<N, E> graph,
        CostFunction<N, E, W> costFunction,
        WeightAlgebra<W> algebra
    );

    @Override
    public Path<N, E, W> solve(N source, N target);
}
```

### Core loop pseudocode

```
function dijkstra(graph, source, target, cost, algebra):
    dist = Map<N, W>           // best known distance
    prev = Map<N, Edge<N,E>>   // previous edge on the path
    heap = MinHeap<N>          // compared by dist[n]

    dist[source] = algebra.zero()
    heap.insert(source)

    while heap is not empty:
        u = heap.extractMin()

        if u == target:
            return reconstructPath(prev, source, target, dist[target])

        for edge in graph.neighbors(u):
            v = edge.target()
            newDist = algebra.add(dist[u], cost.cost(edge))

            if newDist < dist.getOrDefault(v, algebra.infinity()):
                dist[v] = newDist
                prev[v] = edge
                if heap.contains(v):
                    heap.decreaseKey(v)
                else:
                    heap.insert(v)

    return Path.notFound(algebra.infinity())
```

### What to copy from GH

Core loop from `routing/Dijkstra.java` (132 lines) — well written,
but requires "translating" from int nodeIds + double weights to generic `<N, E, W>`.
Key elements to preserve:
- Early termination when `u == target`
- `visitedNodes` counter for diagnostics
- Guard clause on `isInfinite(newDist)` — skip unreachable

---

## AStar\<N, E, W\>

A* = Dijkstra + heuristic. Explores fewer nodes at the cost of
an additional `add` operation for the heuristic.

```java
/**
 * A* algorithm — Dijkstra with a heuristic guiding the search toward the target.
 *
 * The heuristic must be admissible: estimate(n, target) <= realCost(n, target)
 * If not — the result may not be optimal.
 *
 * Complexity: O((V + E) log V) worst case, but in practice much faster
 * than Dijkstra thanks to the heuristic (fewer visited nodes).
 */
public class AStar<N, E, W> implements ShortestPathSolver<N, E, W> {

    public AStar(
        Graph<N, E> graph,
        CostFunction<N, E, W> costFunction,
        WeightAlgebra<W> algebra,
        Heuristic<N, W> heuristic
    );

    @Override
    public Path<N, E, W> solve(N source, N target);
}
```

### Difference vs Dijkstra

In the heap, the priority is `f(n) = g(n) + h(n)`:
- `g(n)` = cost so far from source
- `h(n)` = heuristic estimate of cost to target

```
// In Dijkstra:
heap priority = dist[v]

// In A*:
heap priority = dist[v] + heuristic.estimate(v, target)
```

---

## BidirectionalDijkstra\<N, E, W\>

Search from both sides (from source and from target) — they meet in the middle.

```java
/**
 * Bidirectional Dijkstra — search from both sides.
 *
 * Requires the graph to support reverse navigation.
 * Two strategies:
 * 1. Graph implements an additional interface with reversed neighbors
 * 2. A separate Graph for the backward direction (reversed graph)
 *
 * Complexity: ~O((V + E) log V / 2) — in practice ~2x faster than Dijkstra
 * on large graphs (visits fewer nodes in total).
 */
public class BidirectionalDijkstra<N, E, W> implements ShortestPathSolver<N, E, W> {

    public BidirectionalDijkstra(
        Graph<N, E> forwardGraph,
        Graph<N, E> backwardGraph,
        CostFunction<N, E, W> costFunction,
        WeightAlgebra<W> algebra
    );

    @Override
    public Path<N, E, W> solve(N source, N target);
}
```

### Meet-in-the-middle

```
forward:  source → ... → meeting point
backward: target → ... → meeting point

Stopping condition: when the sum of the best distances from both sides
>= the best found path through the meeting point.
```

For undirected graphs, `backwardGraph` = the same graph
(neighbors in both directions are identical).

For directed graphs, a reversed graph must be provided
or an implementation with `reverseNeighbors()`.

---

## Common elements

### Diagnostics

Each algorithm exposes after executing `solve()`:
- `getVisitedNodes()` — number of visited nodes (diagnostic)

### Validation

Algorithms throw `IllegalArgumentException` when:
- `source` or `target` do not exist in the graph
- `graph` is null, `costFunction` is null, `algebra` is null

### Thread safety

Algorithms are **not thread-safe**. Each thread should create
its own solver instance. However, `Graph`, `CostFunction`, and `WeightAlgebra`
can be shared (they are read-only / stateless).

---

## Implementation order

1. **MinHeap\<T\>** — foundation, needed by all algorithms
2. **SimplePath\<N,E,W\>** — default implementation of `Path` (record/class)
3. **Dijkstra** — simplest, base algorithm
4. **AStar** — extension of Dijkstra with a heuristic
5. **BidirectionalDijkstra** — more complex, requires reversed graph
