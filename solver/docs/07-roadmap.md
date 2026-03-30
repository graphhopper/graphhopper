# Roadmap & decision log

---

## Implementation phases

### Phase 1 — Foundation (solver-api) ✅
- [x] Gradle multi-module structure
- [x] `Edge<N,E>` record
- [x] `Graph<N,E>` interface → minimal contract: `neighbors()` + `containsNode()`
- [x] `FiniteGraph<N,E> extends Graph` — adds `nodes()` for graphs with enumerable nodes
- [x] `CostFunction<N,E,W>` interface
- [x] `WeightAlgebra<W>` interface + `DoubleAlgebra`, `LongAlgebra`, `IntAlgebra`
- [x] `Heuristic<N,W>` interface
- [x] `Path<N,E,W>` interface + `Path.notFound()`
- [x] `ShortestPathSolver<N,E,W>` interface
- [x] Compilation tests — everything compiles, interfaces connect properly

### Phase 2 — Dijkstra (solver-core) ✅
- [x] `MinHeap<T>` with decreaseKey
- [x] `SimplePath<N,E,W>` — default Path implementation
- [x] `Dijkstra<N,E,W>` — classic single-source
- [x] Tests on manually built graphs (ASCII art)
- [x] Test: linear graph A→B→C→D
- [x] Test: graph with multiple paths (confirming optimality)
- [x] Test: unreachable target → `Path.notFound()`
- [x] Test: source == target → path of length 0
- [x] Test: empty graph → `Path.notFound()`

### Phase 3 — A* + Bidirectional (solver-core) ✅
- [x] `AStar<N,E,W>` with heuristic
- [x] `BidirectionalDijkstra<N,E,W>`
- [x] Tests: A* gives the same result as Dijkstra (but fewer visited nodes)
- [x] Tests: Bidirectional gives the same result as Dijkstra
- [x] Property-based test: random graph → comparison of results across all 3 algorithms

### Phase 4 — Graphs (solver-graph) ✅
- [x] `AdjacencyListGraph<N,E>` with builder pattern — implements `FiniteGraph`
- [x] `GridGraph` with 4/8 connectivity — implements `FiniteGraph`, O(1) `containsNode()`
- [x] `GridNode` interface + private `Cell` record, `GridEdge` record
- [x] `GridHeuristics` factory — manhattan, chebyshev, octile, euclidean
- [x] `ReversedGraph<N,E>` wrapper — accepts `FiniteGraph`, implements `FiniteGraph`
- [x] Tests: 5x5 maze with A* + Manhattan heuristic
- [x] End-to-end: build graph with builder → solve with Dijkstra → verify result

### Future (not in v0.1)
- [ ] `solver-int` — IntGraph, IntDijkstra, IntMinHeap
- [ ] Contraction Hierarchies (CH)
- [ ] Landmarks (LM)
- [ ] Pareto multi-criteria (multidimensional weights)
- [ ] DijkstraOneToMany (isochrone / distance matrix)
- [ ] Alternative routes (k-shortest paths)

---

## Decision log

### D1: Genericity `<N, E, W>` instead of hardcoded int/double

**Decision:** Full type parameterization.

**Reason:** GraphHopper hardcodes `int` node IDs and `double` weights. This forces
a concrete storage model at the algorithm API level. Our solver is meant to be
a general-purpose library — city graphs, social networks,
dependency graphs, game maps — with arbitrary types.

**Trade-off:** Boxing overhead on large graphs. Solution: `solver-int` (Path C).

### D2: `Graph<N, E>` without parameter `W`

**Decision:** The graph stores topology and edge data, but NOT weights.

**Reason:** Weight is a function of the edge, not its property. The same road
graph can have cost = distance, time, tolls, or a combination.
`CostFunction<N,E,W>` computes the weight from edge data `E`.

### D3: Linear ordering of weights (not Pareto)

**Decision:** `W extends Comparable<W>` — total ordering.

**Reason:** Pareto multi-criteria (e.g., simultaneously minimize time and cost)
requires a fundamentally different algorithm (Pareto labels, dominance).
We start with a simpler model that covers 90% of use cases.
Pareto as a future extension.

### D4: WeightAlgebra as an ordered monoid with infinity

**Decision:** `WeightAlgebra<W>` defines `zero()`, `infinity()`, `add()`, `compare()`.

**Reason:** Dijkstra requires exactly these operations:
- `zero()` — initial distance of source
- `infinity()` — distance of unreachable nodes
- `add()` — cost accumulation along the path
- `compare()` — minimum selection in the heap

Mathematically this is an ordered monoid with an absorbing element.

### D5: Algebras as enum singletons

**Decision:** `DoubleAlgebra.INSTANCE`, `LongAlgebra.INSTANCE`, `IntAlgebra.INSTANCE`.

**Reason:** Algebra is stateless — there is no point in creating multiple instances.
Enum singleton is the cleanest form in Java (thread-safe, serializable,
singleton guaranteed by JVM).

### D6: Path C — generics first, optimization later

**Decision:** Start with a clean generic model. Int-based fast-path
is added when benchmarks show the need.

**Reason:** It is easier to specialize a good abstraction than to generalize
optimized code. The generic model enforces clean interfaces from the start.

### D7: Zero runtime dependencies

**Decision:** The library has no runtime dependencies (not even SLF4J).

**Reason:** The solver is a library for embedding. Every dependency is a potential
conflict in the user's classpath. JUnit 5 only in test scope.

### D8: Edge data `E` as opaque

**Decision:** The solver does not look into `E` — CostFunction interprets the data.

**Reason:** In GH `EdgeIteratorState` must know the storage format (encoded values,
bit-packing). In our case the algorithm sees `Edge<N, E>` and passes it to
`CostFunction.cost(edge)`. CostFunction knows what is in `E` — the solver does not.

### D9: Graph / FiniteGraph split

**Decision:** `Graph<N,E>` has only `neighbors()` + `containsNode()`.
`FiniteGraph<N,E> extends Graph` adds `nodes()`.

**Reason:** Algorithms (Dijkstra, A*, BidirectionalDijkstra) never
call `nodes()` — they only need `neighbors()` and `containsNode()`.
Previously `Graph.containsNode()` had a default `nodes().contains(n)`,
which forced materialization of the node set — O(rows×cols) for GridGraph.

The split allows algorithms to work with infinite/implicitly defined
graphs, and `FiniteGraph` reserves `nodes()` for operations requiring
enumeration (e.g., `ReversedGraph.buildReversed()`).

`GridGraph.containsNode()` is now O(1) — `isInBounds() && isPassable()`.

---

## What to copy from GraphHopper

| Element | Source in GH | How to adapt |
|---------|-------------|--------------|
| MinHeapWithUpdate | `coll/MinHeapWithUpdate.java` | Generic `MinHeap<T>` with `Comparator` |
| Dijkstra core loop | `routing/Dijkstra.java` (132 lines) | `<N,E,W>` instead of int/double |
| Bidirectional meet-in-middle | `routing/DijkstraBidirectionRef.java` | Two graphs (forward/backward) |
| WeightApproximator concept | `routing/weighting/WeightApproximator.java` | `Heuristic<N,W>` interface |
| CH contraction logic | `routing/ch/` | Future — after Phase 4 |
| Flat-array adjacency | `storage/BaseGraphNodesAndEdges.java` | `solver-int` (future) |

## What NOT to copy

| Element | Why |
|---------|-----|
| Encoded values + `IntsRef` (76 types) | Too complex, tight coupling with storage |
| Janino bytecode compilation | Overkill — a simple `CostFunction` interface is sufficient |
| `EdgeIteratorState` (37+ methods) | God Object — `Edge<N,E>` record is sufficient |
| `QueryGraph` + `VirtualEdgeIteratorState` | 405 lines of boilerplate with God Object |
| `GraphHopper.java` facade | 1705-line God Object — not needed |
| `EncodingManager` | Encoded values registry — not needed with `Edge<N,E>` |
