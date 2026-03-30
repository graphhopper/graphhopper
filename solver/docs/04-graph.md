# solver-graph — Graph implementations

The `solver-graph` module provides concrete implementations of `Graph<N, E>` from solver-api.
It depends **exclusively** on `solver-api`.

Package: `pl.cezarysanecki.solver.graph`

---

## AdjacencyListGraph\<N, E\>

The simplest and most universal implementation — an adjacency list
based on `Map<N, List<Edge<N, E>>>`.

```java
/**
 * Graph based on an adjacency list.
 * Immutable after construction — built via Builder.
 *
 * @param <N> node type
 * @param <E> edge data type
 */
public class AdjacencyListGraph<N, E> implements Graph<N, E> {

    @Override
    public Set<N> nodes();

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

        /** Builds the immutable graph. */
        public AdjacencyListGraph<N, E> build();
    }
}
```

### Complexity

| Operation | Complexity |
|-----------|-----------|
| `nodes()` | O(1) — returns a view |
| `neighbors(n)` | O(1) — returns a list |
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
 * N = GridNode (record: row, col)
 * E = GridEdge (record: direction, e.g. UP/DOWN/LEFT/RIGHT)
 */
public class GridGraph implements Graph<GridNode, GridEdge> {

    public GridGraph(int rows, int cols, Connectivity connectivity);
    public GridGraph(int rows, int cols, Connectivity connectivity,
                     Predicate<GridNode> isPassable);

    @Override
    public Set<GridNode> nodes();

    @Override
    public Iterable<Edge<GridNode, GridEdge>> neighbors(GridNode node);

    public enum Connectivity {
        FOUR,   // up, down, left, right
        EIGHT   // + diagonals
    }
}

public record GridNode(int row, int col) {}

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
var path = dijkstra.solve(new GridNode(0, 0), new GridNode(4, 4));
```

### Manhattan/Euclidean heuristic

GridGraph works naturally with geometric heuristics:

```java
// Manhattan distance (admissible for 4-connectivity)
Heuristic<GridNode, Integer> manhattan = (from, to) ->
    Math.abs(from.row() - to.row()) + Math.abs(from.col() - to.col());

// Euclidean distance (admissible for 8-connectivity)
Heuristic<GridNode, Double> euclidean = (from, to) ->
    Math.hypot(from.row() - to.row(), from.col() - to.col());

var astar = new AStar<>(maze, unitCost, IntAlgebra.INSTANCE, manhattan);
```

---

## ReversedGraph\<N, E\> — wrapper for BidirectionalDijkstra

An adapter that reverses edge directions — needed for BidirectionalDijkstra
on directed graphs.

```java
/**
 * A view of the graph with reversed edges.
 * Delegates to the original graph, but neighbors(n) returns edges
 * incoming to n (instead of outgoing).
 *
 * Requires the original graph to be built with incoming edge information
 * (AdjacencyListGraph builds this automatically).
 */
public class ReversedGraph<N, E> implements Graph<N, E> {

    public ReversedGraph(Graph<N, E> original);

    @Override
    public Set<N> nodes();

    @Override
    public Iterable<Edge<N, E>> neighbors(N node);
}
```

For undirected graphs, `ReversedGraph` is not needed —
`addUndirectedEdge` adds edges in both directions.

---

## Summary

| Implementation | N | E | Memory | Use case |
|---------------|---|---|--------|----------|
| `AdjacencyListGraph` | any | any | O(V+E) | General graphs, road networks, social graphs |
| `GridGraph` | `GridNode` | `GridEdge` | O(1) | Mazes, tile maps, BFS on a grid |
| `ReversedGraph` | any | any | O(1) wrapper | Bidirectional search on directed graphs |
