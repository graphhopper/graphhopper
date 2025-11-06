package com.graphhopper.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.util.shapes.GHPoint3D;

import java.util.List;
import java.util.Objects;

/**
 * Calculates turn angles between edges to determine the straightest path continuation.
 * <p>
 * This calculator helps path-finding algorithms select edges that maintain the most
 * gradual direction changes at intersections.
 */
public class EdgeAngleCalculator {
    private final Graph graph;

    private static final int MINIMUM_POINTS_FOR_BEARING = 2;

    public EdgeAngleCalculator(Graph graph) {
        this.graph = graph;
    }

    /**
     * Selects the edge that continues most straight from the current edge.
     *
     * @param candidateEdges list of edge IDs to evaluate (must not be null or empty)
     * @param currentState   the current edge state we're coming from (must not be null)
     * @param commonNode     the node where the current edge meets the candidate edges
     * @return the edge ID with the straightest continuation
     * @throws IllegalArgumentException if candidateEdges is null/empty or currentState is null
     */
    public int selectStraightestEdge(List<Integer> candidateEdges, EdgeIteratorState currentState, int commonNode) {
        validateInputs(candidateEdges, currentState);

        if (candidateEdges.size() == 1) {
            return candidateEdges.get(0);
        }

        int straightestEdge = candidateEdges.get(0);
        double bestAlignment = Double.NEGATIVE_INFINITY;

        Vector2D entranceVector = getEdgeVector(currentState, commonNode, true);

        for (Integer edgeId : candidateEdges) {
            EdgeIteratorState candidate = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            Vector2D exitVector = getEdgeVector(candidate, commonNode, false);

            double normalizedDotProduct = getNormalizedDotProduct(entranceVector, exitVector);

            if (normalizedDotProduct > bestAlignment) {
                bestAlignment = normalizedDotProduct;
                straightestEdge = edgeId;
            }
        }

        return straightestEdge;
    }

    /**
     * Validates that the input parameters are not null or empty.
     *
     * @param candidateEdges list of candidate edges to validate
     * @param currentState   current edge state to validate
     * @throws IllegalArgumentException if candidateEdges is null or empty
     * @throws NullPointerException if currentState is null
     */
    private void validateInputs(List<Integer> candidateEdges, EdgeIteratorState currentState) {
        if (candidateEdges == null || candidateEdges.isEmpty()) {
            throw new IllegalArgumentException("Candidate edges cannot be null or empty");
        }
        Objects.requireNonNull(currentState, "Current state cannot be null");
    }

    /**
     * Calculates the normalized dot product between two vectors.
     * <p>
     * This method computes (A · B) / |B|, which is an optimized version of the standard
     * normalized dot product formula (A · B) / (|A| * |B|). Since |A| (the entrance vector magnitude)
     * is constant for all comparisons in the same iteration, it's excluded from the calculation
     * to improve performance.
     *
     * @param entranceVector the vector representing the approach to the common node
     * @param exitVector     the vector representing the departure from the common node
     * @return the normalized dot product value, where higher values indicate straighter continuation
     */
    private static double getNormalizedDotProduct(Vector2D entranceVector, Vector2D exitVector) {
        double dotProduct = entranceVector.dotProduct(exitVector);
        double exitMagnitudeSquared = exitVector.magnitudeSquared();

        return dotProduct / Math.sqrt(exitMagnitudeSquared);
    }

    /**
     * Gets the vector representation of an edge relative to a specific node.
     *
     * @param edge        the edge to get the vector for
     * @param nodeId      the reference node
     * @param isIncoming  if true, returns the vector of the edge arriving at the node (entrance vector);
     *                    if false, returns the vector of the edge departing from the node (exit vector)
     * @return a 2D vector representation
     */
    private Vector2D getEdgeVector(EdgeIteratorState edge, int nodeId, boolean isIncoming) {
        PointList points = edge.fetchWayGeometry(FetchMode.ALL);

        if (points.size() < MINIMUM_POINTS_FOR_BEARING) {
            throw new IllegalStateException(
                    String.format("Edge must have at least %d points to calculate vector, but has %d",
                            MINIMUM_POINTS_FOR_BEARING, points.size())
            );
        }

        int lastIndex = points.size() - 1;
        boolean reverseDirection = isIncoming
                ? edge.getAdjNode() != nodeId
                : edge.getBaseNode() != nodeId;

        GHPoint3D from = points.get(reverseDirection ? lastIndex : 0);
        GHPoint3D to = points.get(reverseDirection ? 0 : lastIndex);

        return new Vector2D(to.lat - from.lat, to.lon - from.lon);
    }

    /**
     * 2D vector record for dot product calculations.
     */
    private record Vector2D(double x, double y) {
        /**
         * Calculates the dot product of this vector with another vector.
         *
         * @param other the other vector
         * @return the dot product (x1*x2 + y1*y2)
         */
        double dotProduct(Vector2D other) {
            return this.x * other.x + this.y * other.y;
        }

        /**
         * Calculates the squared magnitude of this vector.
         * This is an optimization to avoid the expensive sqrt operation when only comparing magnitudes.
         *
         * @return the squared magnitude (x^2 + y^2)
         */
        double magnitudeSquared() {
            return x * x + y * y;
        }
    }
}
