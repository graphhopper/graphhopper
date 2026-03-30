# Solver — Project Overview

A generic library for finding shortest paths in graphs.
Inspired by GraphHopper's architecture, but with clean abstractions,
full type parameterization `<N, E, W>`, and zero coupling
between algorithms and storage.

---

## Motivation — why not GraphHopper

GraphHopper is a powerful routing engine, but its core has architectural issues
that prevent easy extraction and reuse of algorithms:

| Problem | Details |
|---------|---------|
| `EdgeIteratorState` — God Object | 37+ methods. A routing algorithm needs **4**. The rest is import, instructions, raw storage. |
| `Graph.getBaseGraph()` | Returns a concrete class `BaseGraph` — makes swapping implementations impossible. |
| `GraphHopper.java` | 1705 lines — a facade holding absolutely everything. |
| Encoded values + Janino | 76 types of bit-packed properties + runtime bytecode compilation. Powerful, but overkill for many use cases. |
| CH/LM preprocessing | Coupled with `BaseGraph` (concrete class), not with a graph interface. |

Our solver addresses these problems through:
- **Minimal interfaces** — `Graph<N, E>` has 3 methods, not 14
- **Zero coupling** — the algorithm knows nothing about storage
- **Full genericity** — works with any node, edge, and weight type

---

## Type parameters `<N, E, W>`

Solver is fully parameterized with three types:

| Parameter | Meaning | Requirements |
|-----------|---------|--------------|
| `N` | **Node** — node type | `equals()` + `hashCode()` (key in maps) |
| `E` | **Edge data** — data on the edge | Solver does not look inside — CostFunction reads E |
| `W` | **Weight** — weight type | `Comparable<W>` — linear ordering |

### Why doesn't `Graph<N, E>` have `W`?

The graph stores **topology and edge data**. Weight is **computed** by `CostFunction<N, E, W>`,
not stored in the graph. The same graph can be used with different cost functions
(e.g., distance vs time vs toll costs).

### Simplification: linear ordering of weights

Weights have a **linear order** (`Comparable<W>`). This means:
- For any two weights `a` and `b`: either `a < b`, or `a > b`, or `a == b`
- **No Pareto multi-criteria** — we do not support simultaneous optimization
  of multiple dimensions (e.g., time + cost)

Pareto is planned as a future extension, but not in the first version.
This simplification allows for simpler, more readable code and standard
algorithms (Dijkstra, A*) without modifications.

---

## Implementation strategy — Path C

We chose "Path C":

1. **Start with a clean generic model** — `<N, E, W>` with full abstraction
2. **Optimize later** — when performance on large graphs requires specialization
3. **Int-based fast-path** — `IntGraphSolver` (N=int, W=double, zero boxing) with an adapter from the generic model

Why not the other way around (start with int)?
- The generic model enforces good interfaces from the start
- It's easier to specialize a good abstraction than to generalize optimized code
- For graphs with <100k nodes, the generic model is fast enough

---

## Architecture — 4 modules

```
solver-api       (interfaces — zero implementations, zero dependencies)
     │
solver-core      (algorithms: Dijkstra, A*, BidirectionalDijkstra)
     │
solver-graph     (graph implementations: AdjacencyListGraph, GridGraph)

solver-int       (int-based specialization — future)
```

### solver-api
Pure Java interfaces: `Graph`, `Edge`, `CostFunction`, `WeightAlgebra`,
`Heuristic`, `Path`, `ShortestPathSolver`. Zero implementations, zero dependencies.

### solver-core
Algorithms operating **exclusively** on interfaces from solver-api:
- `Dijkstra<N, E, W>`
- `AStar<N, E, W>`
- `BidirectionalDijkstra<N, E, W>`
- `MinHeap<T>` — priority queue with decreaseKey

### solver-graph
Concrete implementations of `Graph<N, E>`:
- `AdjacencyListGraph<N, E>` — `Map<N, List<Edge<N,E>>>`, builder pattern
- `GridGraph` — 2D grid with 4/8 neighbors

### solver-int (future)
Int-based fast-path: N=int, W=double, flat-array adjacency list.
Adapter from the generic model. Not implemented in Phase 1.

---

## Comparison with GraphHopper

| Aspect | GraphHopper | Solver |
|--------|-------------|--------|
| Edge interface | 37+ methods (God Object) | `Edge<N,E>` record — 3 fields |
| Graph interface | 14 methods + concrete leak | 3 methods, clean generics |
| Node type | always `int` | any `N` with equals/hashCode |
| Weight type | always `double` | any `W extends Comparable<W>` |
| Algorithm knows about storage? | Yes (`IntsRef`, `EncodedValue`) | No |
| New graph backend | Practically impossible | Implement `Graph<N, E>` |
| Custom cost function | Janino bytecode compilation | Interface `CostFunction<N,E,W>` |
| Runtime dependencies | SLF4J, Jackson, Janino, ... | **Zero** |
| Minimum JAR for routing | ~5MB (entire core) | ~50KB (solver-core) |

---

## Technologies

- **Language:** Java 24
- **Build:** Gradle Kotlin DSL
- **Tests:** JUnit 5
- **Runtime dependencies:** zero
- **Package:** `pl.cezarysanecki.solver`
