# solver-api — Interfaces

The `solver-api` module defines pure interfaces without any implementations
and without any runtime dependencies. These are the contracts on which algorithms operate.

Package: `pl.cezarysanecki.solver.api`

---

## Edge\<N, E\> — graph edge

A record representing an edge. Solver treats `data` (`E`) as opaque —
it does not look inside. Only `CostFunction` knows how to read edge data.

```java
package pl.cezarysanecki.solver.api;

/**
 * A graph edge connecting two nodes with associated data.
 *
 * @param source source node
 * @param target target node
 * @param data   edge data (opaque to the solver — read by CostFunction)
 * @param <N>    node type
 * @param <E>    edge data type
 */
public record Edge<N, E>(N source, N target, E data) {

    /**
     * Returns an edge with swapped source/target (for undirected graphs).
     */
    public Edge<N, E> reversed() {
        return new Edge<>(target, source, data);
    }
}
```

### Why a record, not an interface?

- An edge is a **value** (value object) — it does not need polymorphism
- A record gives us equals/hashCode/toString for free
- Minimizes ceremony — 1 line instead of a class with getters

---

## Graph\<N, E\> — graph (topology + edge data)

```java
package pl.cezarysanecki.solver.api;

import java.util.Set;

/**
 * Read-only graph — topology + edge data.
 * Has no W parameter — weight is computed by CostFunction, not stored.
 *
 * @param <N> node type (must have proper equals/hashCode)
 * @param <E> edge data type
 */
public interface Graph<N, E> {

    /**
     * Returns the set of all nodes in the graph.
     */
    Set<N> nodes();

    /**
     * Returns the neighbors (outgoing edges) of a given node.
     * For a node not present in the graph, returns an empty iterable.
     */
    Iterable<Edge<N, E>> neighbors(N node);

    /**
     * Checks whether the graph contains a given node.
     */
    default boolean containsNode(N node) {
        return nodes().contains(node);
    }
}
```

### Key decisions

1. **No `W` in Graph** — the graph stores topology and edge data.
   Weight is **computed** by CostFunction. The same graph can be
   used with different cost functions.

2. **`Iterable` instead of `List`/`Collection`** — allows lazy generation
   of neighbors (e.g., GridGraph generates neighbors on-the-fly, does not hold them in memory).

3. **`Set<N> nodes()`** — needed for validation and iteration. Can be a lazy view.

---

## CostFunction\<N, E, W\> — cost function

```java
package pl.cezarysanecki.solver.api;

/**
 * Computes the cost (weight) of traversing an edge.
 * This is the only place that "looks inside" edge data E
 * and produces a weight W.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
@FunctionalInterface
public interface CostFunction<N, E, W> {

    /**
     * Computes the weight of traversing a given edge.
     *
     * @param edge the edge to price
     * @return weight (cost) of traversal — must be non-negative
     */
    W cost(Edge<N, E> edge);
}
```

### Why `@FunctionalInterface`?

Allows lambdas:

```java
CostFunction<String, Road, Double> byCost =
    edge -> edge.data().tollCost();

CostFunction<String, Road, Double> byDistance =
    edge -> edge.data().distanceKm();
```

---

## WeightAlgebra\<W\> — weight algebra

Mathematically: an **ordered monoid with an absorbing element (infinity)**.

```java
package pl.cezarysanecki.solver.api;

import java.util.Comparator;

/**
 * Defines algebraic operations on weights.
 *
 * Requirements:
 * - zero() is the identity element of add: add(zero(), w) == w
 * - infinity() is the absorbing element: add(infinity(), w) == infinity()
 * - add is associative: add(add(a, b), c) == add(a, add(b, c))
 * - ordering is linear (total): for any a, b: a <= b or b <= a
 * - weights are non-negative: compare(w, zero()) >= 0 for every w
 *
 * @param <W> weight type
 */
public interface WeightAlgebra<W> extends Comparator<W> {

    /** Identity element of addition — zero cost. */
    W zero();

    /** Absorbing element — unreachability. */
    W infinity();

    /** Sum of two weights (associative, commutative). */
    W add(W a, W b);

    /** Does the given weight represent infinity (unreachability)? */
    default boolean isInfinite(W weight) {
        return compare(weight, infinity()) >= 0;
    }

    /** Is a < b? Convenience method. */
    default boolean isLessThan(W a, W b) {
        return compare(a, b) < 0;
    }

    /** Is a <= b? Convenience method. */
    default boolean isLessOrEqual(W a, W b) {
        return compare(a, b) <= 0;
    }
}
```

### Ready-made implementations (enum singletons)

```java
/** Algebra for Double — the most common case. */
public enum DoubleAlgebra implements WeightAlgebra<Double> {
    INSTANCE;

    @Override public Double zero() { return 0.0; }
    @Override public Double infinity() { return Double.POSITIVE_INFINITY; }
    @Override public Double add(Double a, Double b) { return a + b; }
    @Override public int compare(Double a, Double b) { return Double.compare(a, b); }
}

/** Algebra for Long — integer weights (e.g., milliseconds). */
public enum LongAlgebra implements WeightAlgebra<Long> {
    INSTANCE;

    @Override public Long zero() { return 0L; }
    @Override public Long infinity() { return Long.MAX_VALUE; }
    @Override public Long add(Long a, Long b) {
        // overflow protection
        if (a == Long.MAX_VALUE || b == Long.MAX_VALUE) return Long.MAX_VALUE;
        return a + b;
    }
    @Override public int compare(Long a, Long b) { return Long.compare(a, b); }
}

/** Algebra for Integer — lightweight integer weights. */
public enum IntAlgebra implements WeightAlgebra<Integer> {
    INSTANCE;

    @Override public Integer zero() { return 0; }
    @Override public Integer infinity() { return Integer.MAX_VALUE; }
    @Override public Integer add(Integer a, Integer b) {
        if (a == Integer.MAX_VALUE || b == Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return a + b;
    }
    @Override public int compare(Integer a, Integer b) { return Integer.compare(a, b); }
}
```

---

## Heuristic\<N, W\> — A* heuristic

```java
package pl.cezarysanecki.solver.api;

/**
 * A heuristic estimating the lower bound of cost from a node to the target.
 * Used by A* to speed up the search.
 *
 * Requirement (admissibility): estimate(n, target) <= realCost(n, target)
 * The heuristic must not overestimate the cost.
 *
 * @param <N> node type
 * @param <W> weight type
 */
@FunctionalInterface
public interface Heuristic<N, W> {

    /**
     * Estimates the lower bound of cost from {@code from} to {@code to}.
     */
    W estimate(N from, N to);
}
```

---

## Path\<N, E, W\> — search result

```java
package pl.cezarysanecki.solver.api;

import java.util.List;

/**
 * The result of a shortest path search.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public interface Path<N, E, W> {

    /** List of nodes on the path (from source to target inclusive). */
    List<N> nodes();

    /** List of edges on the path (nodes.size() - 1 elements). */
    List<Edge<N, E>> edges();

    /** Total weight of the path. */
    W totalWeight();

    /** Was a path found? false = target unreachable. */
    boolean isFound();

    /** Empty path — target unreachable. */
    static <N, E, W> Path<N, E, W> notFound(W infiniteWeight) {
        return new Path<>() {
            @Override public List<N> nodes() { return List.of(); }
            @Override public List<Edge<N, E>> edges() { return List.of(); }
            @Override public W totalWeight() { return infiniteWeight; }
            @Override public boolean isFound() { return false; }
        };
    }
}
```

---

## ShortestPathSolver\<N, E, W\> — solver interface

```java
package pl.cezarysanecki.solver.api;

/**
 * Shortest path solver.
 * Each implementation (Dijkstra, A*, Bidirectional) implements this interface.
 *
 * @param <N> node type
 * @param <E> edge data type
 * @param <W> weight type
 */
public interface ShortestPathSolver<N, E, W> {

    /**
     * Finds the shortest path from {@code source} to {@code target}.
     *
     * @return Path with isFound()==true if a path exists,
     *         Path.notFound() otherwise
     */
    Path<N, E, W> solve(N source, N target);
}
```

---

## Interface summary

| Interface | Parameters | Methods | Role |
|-----------|------------|---------|------|
| `Edge<N,E>` | N, E | record — 3 fields | Edge value |
| `Graph<N,E>` | N, E | 3 | Topology + edge data |
| `CostFunction<N,E,W>` | N, E, W | 1 | Edge → Weight |
| `WeightAlgebra<W>` | W | 5 + convenience | Weight operations |
| `Heuristic<N,W>` | N, W | 1 | Lower bound of cost (A*) |
| `Path<N,E,W>` | N, E, W | 4 | Search result |
| `ShortestPathSolver<N,E,W>` | N, E, W | 1 | Solver contract |

Total: **7 types, ~15 methods**. For comparison, `EdgeIteratorState` in GH alone has 37+ methods.
