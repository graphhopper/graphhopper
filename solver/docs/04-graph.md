# solver-graph — Graph Implementations

The `solver-graph` module provides concrete implementations of `FiniteGraph<N, E>` from solver-api.
It depends **exclusively** on `solver-api`.

Package: `pl.cezarysanecki.solver.graph`

---

## AdjacencyListGraph\<N, E\>

The simplest and most universal implementation — an adjacency list
based on `Map<N, List<Edge<N, E>>>`.

```java
/**
 * Graph based on an adjacency list.
 * Immutable after construction — built by Builder.
 *
 * @param <N> node type
 * @param <E> edge data type
 */
public class AdjacencyListGraph<N, E> implements FiniteGraph<N, E> {

    @Override
    public Set<N> nodes();

    @Override
    public boolean containsNode(N node);

    @Override
    public Iterable<Edge<N, E>> neighbors(N node);

    /** Builder for constructing the graph. */
    public static <N, E> Builder<N, E> builder() { ... }

    public static class Builder<N, E> {

        /** Adds a node (optional — nodes are added automatically with addEdge). */
        public Builder<N, E> addNode(N node);

        /** Adds a directed edge. */
        public Builder<N, E> addEdge(N source, N target, E data);

        /** Adds an undirected edge (two directed edges). */
        public Builder<N, E> addUndirectedEdge(N source, N target, E data);

        /** Builds an immutable graph. */
        public AdjacencyListGraph<N, E> build();
    }
}
```

### Complexity

| Operation | Complexity |
|-----------|-----------|
| `nodes()` | O(1) — returns a view |
| `neighbors(n)` | O(1) — returns the list |
| `containsNode(n)` | O(1) — HashMap.containsKey |
| Memory | O(V + E) |

### Usage example

```java
//  A ---5--- B
//  |         |
//  2         3
//  |         |
//  C ---1--- D

var graph = AdjacencyListGraph.<String, Double>builder()
    .addUndirectedEdge("A", "B", 5.0)
    .addUndirectedEdge("A", "C", 2.0)
    .addUndirectedEdge("B", "D", 3.0)
    .addUndirectedEdge("C", "D", 1.0)
    .build();

CostFunction<String, Double, Double> byWeight = edge -> edge.data();

var dijkstra = new Dijkstra<>(graph, byWeight, DoubleAlgebra.INSTANCE);
Path<String, Double, Double> path = dijkstra.solve("A", "D");

// path.nodes() == ["A", "C", "D"]
// path.totalWeight() == 3.0
```

---

## GridGraph — 2D grid graph

A specialization for regular 2D graphs (maze, tile map, BFS on a grid).
Does not store edges in memory — generates neighbors on-the-fly.

```java
/**
 * 2D grid graph — nodes are (row, col) pairs.
 * Edges generated dynamically (4 or 8 neighbors).
 * Optional predicate for blocking cells (maze walls).
 *
 * N = GridNode (interface: row(), col())
 * E = GridEdge (record: direction, e.g. UP/DOWN/LEFT/RIGHT)
 */
public class GridGraph implements FiniteGraph<GridNode, GridEdge> {

    public GridGraph(int rows, int cols, Connectivity connectivity);
    public GridGraph(int rows, int cols, Connectivity connectivity,
                     Predicate<GridNode> isPassable);

    @Override
    public Set<GridNode> nodes();

    @Override
    public boolean containsNode(GridNode node);  // O(1) — isInBounds + isPassable

    @Override
    public Iterable<Edge<GridNode, GridEdge>> neighbors(GridNode node);

    /** Creates a node compatible with this graph. */
    public GridNode node(int row, int col);

    public enum Connectivity {
        FOUR,   // up, down, left, right
        EIGHT   // + diagonals
    }
}

/**
 * Grid node — interface, not record.
 * GridGraph internally uses a private Cell record.
 * In tests you can use SimpleGridNode (test-only) for heuristics.
 */
public interface GridNode {
    int row();
    int col();
}

public record GridEdge(Direction direction) {
    public enum Direction {
        UP, DOWN, LEFT, RIGHT,
        UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
    }
}
```

### Complexity

| Operation | Complexity |
|-----------|-----------|
| `nodes()` | O(rows × cols) — generated |
| `neighbors(n)` | O(1) — max 4 or 8 neighbors, generated on-the-fly |
| `containsNode(n)` | O(1) — `isInBounds() && isPassable()` |
| Memory | O(1) — zero stored edges (plus predicate if provided) |

### Example: maze

```java
// 5x5 grid with blocked cells
boolean[][] walls = {
    {false, false, false, false, false},
    {false, true,  true,  true,  false},
    {false, false, false, true,  false},
    {false, true,  false, false, false},
    {false, false, false, false, false},
};

var maze = new GridGraph(5, 5, Connectivity.FOUR,
    node -> !walls[node.row()][node.col()]);

// Cost = 1 per step
CostFunction<GridNode, GridEdge, Integer> unitCost = edge -> 1;

var dijkstra = new Dijkstra<>(maze, unitCost, IntAlgebra.INSTANCE);
var path = dijkstra.solve(maze.node(0, 0), maze.node(4, 4));
```

### Heuristics — GridHeuristics factory

The `pl.cezarysanecki.solver.graph.heuristics` package provides ready-made geometric
heuristics for `GridNode`:

```java
/**
 * Heuristics factory for GridNode.
 * Returns Heuristic<GridNode, Double>.
 */
public final class GridHeuristics {

    /** Manhattan distance — admissible for FOUR connectivity. */
    public static Heuristic<GridNode, Double> manhattan();

    /** Chebyshev distance — admissible for EIGHT connectivity. */
    public static Heuristic<GridNode, Double> chebyshev();

    /** Octile distance — admissible for EIGHT connectivity with sqrt(2) weights on diagonals. */
    public static Heuristic<GridNode, Double> octile();

    /** Euclidean distance — admissible for both connectivities (but looser). */
    public static Heuristic<GridNode, Double> euclidean();
}
```

Usage example with A*:

```java
var maze = new GridGraph(5, 5, Connectivity.FOUR,
    node -> !walls[node.row()][node.col()]);

CostFunction<GridNode, GridEdge, Double> unitCost = edge -> 1.0;

var astar = new AStar<>(maze, unitCost, DoubleAlgebra.INSTANCE,
    GridHeuristics.manhattan());
var path = astar.solve(maze.node(0, 0), maze.node(4, 4));
```

---

## ReversedGraph\<N, E\> — wrapper for BidirectionalDijkstra

An adapter that reverses edge directions — needed for BidirectionalDijkstra
on directed graphs.

Accepts `FiniteGraph` (not `Graph`), because it must iterate over all
nodes to build the reversed adjacency list.

```java
/**
 * A view of the graph with reversed edges.
 * Requires a one-time scan of the original graph (in the constructor).
 * Therefore it accepts FiniteGraph — it needs nodes() for iteration.
 */
public class ReversedGraph<N, E> implements FiniteGraph<N, E> {

    public ReversedGraph(FiniteGraph<N, E> original);

    @Override
    public Set<N> nodes();

    @Override
    public boolean containsNode(N node);

    @Override
    public Iterable<Edge<N, E>> neighbors(N node);
}
```

For undirected graphs `ReversedGraph` is not needed —
`addUndirectedEdge` adds edges in both directions.

---

## Summary

| Implementation | N | E | Memory | Use case |
|---------------|---|---|--------|----------|
| `AdjacencyListGraph` | any | any | O(V+E) | General graphs, road networks, social graphs |
| `GridGraph` | `GridNode` | `GridEdge` | O(1) | Mazes, tile maps, BFS on a grid |
| `ReversedGraph` | any | any | O(V+E) | Bidirectional search on directed graphs |

**Note:** All implementations implement `FiniteGraph<N, E>` (not just `Graph`),
so they expose `nodes()`, `containsNode()`, and `neighbors()`.
