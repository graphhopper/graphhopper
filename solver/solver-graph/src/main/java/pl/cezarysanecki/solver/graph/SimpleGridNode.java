package pl.cezarysanecki.solver.graph;

/**
 * Default implementation of {@link GridNode} — an immutable grid vertex.
 * <p>
 * Used internally by {@link GridGraph}.
 *
 * @param row row (0-indexed)
 * @param col column (0-indexed)
 */
public record SimpleGridNode(int row, int col) implements GridNode {}
