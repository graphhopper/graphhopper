# Roadmap & decision log

---

## Implementation phases

### Phase 1 — Foundation (solver-api)
- [ ] Gradle multi-module structure
- [ ] `Edge<N,E>` record
- [ ] `Graph<N,E>` interface
- [ ] `CostFunction<N,E,W>` interface
- [ ] `WeightAlgebra<W>` interface + `DoubleAlgebra`, `LongAlgebra`, `IntAlgebra`
- [ ] `Heuristic<N,W>` interface
- [ ] `Path<N,E,W>` interface + `Path.notFound()`
- [ ] `ShortestPathSolver<N,E,W>` interface
- [ ] Compilation tests — everything compiles, interfaces connect

### Phase 2 — Dijkstra (solver-core)
- [ ] `MinHeap<T>` with decreaseKey
- [ ] `SimplePath<N,E,W>` — default implementation of Path
- [ ] `Dijkstra<N,E,W>` — classic single-source
- [ ] Tests on manually built graphs (ASCII art)
- [ ] Test: linear graph A→B→C→D
- [ ] Test: graph with multiple paths (confirm optimality)
- [ ] Test: unreachable target → `Path.notFound()`
- [ ] Test: source == target → path of length 0
- [ ] Test: empty graph → `Path.notFound()`

### Phase 3 — A* + Bidirectional (solver-core)
- [ ] `AStar<N,E,W>` with heuristic
- [ ] `BidirectionalDijkstra<N,E,W>`
- [ ] Tests: A* gives the same result as Dijkstra (but fewer visited nodes)
- [ ] Tests: Bidirectional gives the same result as Dijkstra
- [ ] Property-based test: random graph → compare results of all 3 algorithms

### Phase 4 — Graphs (solver-graph)
- [ ] `AdjacencyListGraph<N,E>` with builder pattern
- [ ] `GridGraph` with 4/8 connectivity
- [ ] `GridNode`, `GridEdge` records
- [ ] `ReversedGraph<N,E>` wrapper
- [ ] Tests: 5x5 maze with A* + Manhattan heuristic
- [ ] End-to-end: build graph via builder → solve with Dijkstra → verify result

### Future (not in v0.1)
- [ ] `solver-int` — IntGraph, IntDijkstra, IntMinHeap
- [ ] Contraction Hierarchies (CH)
- [ ] Landmarks (LM)
- [ ] Pareto multi-criteria (multi-dimensional weights)
- [ ] DijkstraOneToMany (isochrone / distance matrix)
- [ ] Alternative routes (k-shortest paths)

---

## Decision log

### D1: Genericity `<N, E, W>` instead of hardcoded int/double

**Decision:** Full type parameterization.

**Reason:** GraphHopper hardcodes `int` node IDs and `double` weights. This forces
a specific storage model at the algorithm API level. Our solver is meant to be
a general-purpose library — city graphs, social networks,
dependency graphs, game maps — with arbitrary types.

**Trade-off:** Boxing overhead on large graphs. Solution: `solver-int` (Path C).

### D2: `Graph<N, E>` without the `W` parameter

**Decision:** The graph stores topology and edge data, but NOT weights.

**Reason:** Weight is a function of the edge, not its property. The same road
graph can have cost = distance, time, tolls, or a combination.
`CostFunction<N,E,W>` computes the weight from edge data `E`.

### D3: Linear ordering of weights (not Pareto)

**Decision:** `W extends Comparable<W>` — total ordering.

**Reason:** Pareto multi-criteria (e.g., minimize time and cost simultaneously)
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

Mathematically, this is an ordered monoid with an absorbing element.

### D5: Algebras as enum singletons

**Decision:** `DoubleAlgebra.INSTANCE`, `LongAlgebra.INSTANCE`, `IntAlgebra.INSTANCE`.

**Reason:** An algebra is stateless — there is no point in creating multiple instances.
An enum singleton is the cleanest form in Java (thread-safe, serializable,
singleton guaranteed by JVM).

### D6: Path C — generics first, optimization later

**Decision:** Start with a clean generic model. Int-based fast-path
added when benchmarks show the need.

**Reason:** It's easier to specialize a good abstraction than to generalize
optimized code. The generic model enforces clean interfaces from the start.

### D7: Zero runtime dependencies

**Decision:** The library has no runtime dependencies (not even SLF4J).

**Reason:** Solver is a library for embedding. Every dependency is a potential
conflict in the user's classpath. JUnit 5 only in test scope.

### D8: Edge data `E` as opaque

**Decision:** Solver does not look inside `E` — CostFunction interprets the data.

**Reason:** In GH, `EdgeIteratorState` must know the storage format (encoded values,
bit-packing). In our case, the algorithm sees `Edge<N, E>` and passes it to
`CostFunction.cost(edge)`. CostFunction knows what's in `E` — the solver does not.

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
