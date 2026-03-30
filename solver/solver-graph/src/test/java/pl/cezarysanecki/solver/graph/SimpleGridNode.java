package pl.cezarysanecki.solver.graph;

/**
 * Simple implementation of {@link GridNode} for use in tests.
 *
 * @param row row (0-indexed)
 * @param col column (0-indexed)
 */
public record SimpleGridNode(int row, int col) implements GridNode {}
