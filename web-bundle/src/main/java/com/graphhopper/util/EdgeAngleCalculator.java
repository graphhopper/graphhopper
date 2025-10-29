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

    private static final double STRAIGHT_ANGLE_DEGREES = 180.0;
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
        double smallestDeviation = Double.MAX_VALUE;

        for (Integer edgeId : candidateEdges) {
            EdgeIteratorState candidate = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            double turnAngle = calculateTurnAngle(currentState, candidate, commonNode);
            double deviation = calculateDeviationFromStraight(turnAngle);

            if (deviation < smallestDeviation) {
                smallestDeviation = deviation;
                straightestEdge = edgeId;
            }
        }

        return straightestEdge;
    }

    /**
     * Calculates the turn angle between two edges at their common node.
     * <p>
     * Returns 180° for going straight, 90° for a right angle turn, and 0° for a U-turn.
     *
     * @param fromEdge   the edge we're traveling along to reach the common node
     * @param toEdge     the candidate edge we're considering taking from the common node
     * @param commonNode the node where these edges meet
     * @return the turn angle in degrees [0, 180], where higher values indicate straighter paths
     */
    private double calculateTurnAngle(EdgeIteratorState fromEdge, EdgeIteratorState toEdge, int commonNode) {
        double currentApproachBearing = calculateBearingTowardNode(fromEdge, commonNode);
        double currentExitBearing = normalizeBearing(currentApproachBearing + 180.0);
        double candidateApproachBearing = calculateBearingTowardNode(toEdge, commonNode);

        return normalizeAngleDifference(currentExitBearing - candidateApproachBearing);
    }

    /**
     * Calculates the compass bearing of an edge as it approaches a specific node.
     *
     * @param edge   the edge to calculate bearing for
     * @param nodeId the node being approached
     * @return the compass bearing in degrees [0, 360), where 0° is North, 90° is East
     */
    private double calculateBearingTowardNode(EdgeIteratorState edge, int nodeId) {
        PointList points = edge.fetchWayGeometry(FetchMode.ALL);

        if (points.size() < MINIMUM_POINTS_FOR_BEARING) {
            throw new IllegalStateException(
                    String.format("Edge must have at least %d points to calculate bearing, but has %d",
                            MINIMUM_POINTS_FOR_BEARING, points.size())
            );
        }

        boolean isNodeAtEnd = edge.getAdjNode() == nodeId;
        GHPoint3D from, to;

        if (isNodeAtEnd) {
            from = points.get(0);
            to = points.get(1);
        } else {
            int lastIndex = points.size() - 1;
            from = points.get(lastIndex - 1);
            to = points.get(lastIndex);
        }

        return AngleCalc.ANGLE_CALC.calcAzimuth(from.lat, from.lon, to.lat, to.lon);
    }

    /**
     * Calculates how far a turn angle deviates from going straight (180°).
     *
     * @param turnAngle the turn angle in degrees
     * @return the absolute deviation from 180°
     */
    private double calculateDeviationFromStraight(double turnAngle) {
        return Math.abs(STRAIGHT_ANGLE_DEGREES - turnAngle);
    }

    /**
     * Normalizes a bearing to the range [0, 360).
     *
     * @param bearing the bearing to normalize
     * @return the normalized bearing in degrees (0-360)
     */
    private double normalizeBearing(double bearing) {
        bearing = bearing % 360;
        return bearing < 0 ? bearing + 360 : bearing;
    }

    /**
     * Normalizes an angle difference to the range [0, 180].
     *
     * @param diff the angle difference
     * @return the normalized difference in degrees (0-180)
     */
    private double normalizeAngleDifference(double diff) {
        diff = Math.abs(diff);
        return diff > 180 ? 360 - diff : diff;
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
}
