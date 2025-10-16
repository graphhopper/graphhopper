package com.graphhopper.util;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.shapes.GHPoint3D;

/**
 * Helper class containing distance calculation utility methods.
 * Provides methods for calculating distances between points, to edges, and to line segments.
 */
public class DistanceCalculationHelper {

    private final Graph graph;
    private final NodeAccess nodeAccess;

    public DistanceCalculationHelper(Graph graph, NodeAccess nodeAccess) {
        this.graph = graph;
        this.nodeAccess = nodeAccess;
    }

    /**
     * Calculates the distance between two geographic points.
     *
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in meters
     */
    public double calculatePointDistance(double lat1, double lon1, double lat2, double lon2) {
        return DistancePlaneProjection.DIST_PLANE.calcDist(lat1, lon1, lat2, lon2);
    }

    /**
     * Calculates the minimum distance from a point to any point on an edge.
     *
     * @param edge the edge ID
     * @param lat latitude of the target point
     * @param lon longitude of the target point
     * @return minimum distance in meters
     */
    public double calculateDistanceToEdge(Integer edge, double lat, double lon) {
        EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
        PointList pointList = state.fetchWayGeometry(FetchMode.ALL); // Use ALL to get complete geometry

        // If no geometry points found, this shouldn't happen but handle gracefully
        if (pointList.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double minDistance = Double.MAX_VALUE;

        // For single point, just calculate point distance
        if (pointList.size() == 1) {
            GHPoint3D point = pointList.get(0);
            return calculatePointDistance(lat, lon, point.lat, point.lon);
        }

        // For multiple points, calculate distance to each line segment
        for (int i = 0; i < pointList.size() - 1; i++) {
            GHPoint3D point1 = pointList.get(i);
            GHPoint3D point2 = pointList.get(i + 1);

            double segmentDistance = calculateDistanceToLineSegment(lat, lon,
                point1.lat, point1.lon, point2.lat, point2.lon);

            minDistance = Math.min(minDistance, segmentDistance);
        }

        return minDistance;
    }

    /**
     * Calculates the shortest distance from a point to a line segment.
     * Uses geometric calculation to find the perpendicular distance.
     *
     * @param pointLat latitude of the target point
     * @param pointLon longitude of the target point
     * @param segStartLat latitude of line segment start
     * @param segStartLon longitude of line segment start
     * @param segEndLat latitude of line segment end
     * @param segEndLon longitude of line segment end
     * @return distance in meters from point to the closest point on the line segment
     */
    public double calculateDistanceToLineSegment(double pointLat, double pointLon,
                                                double segStartLat, double segStartLon,
                                                double segEndLat, double segEndLon) {
        // Calculate segment vector
        double segDeltaLat = segEndLat - segStartLat;
        double segDeltaLon = segEndLon - segStartLon;

        // Calculate point vector from segment start
        double pointDeltaLat = pointLat - segStartLat;
        double pointDeltaLon = pointLon - segStartLon;

        // Calculate segment length squared
        double segmentLengthSq = segDeltaLat * segDeltaLat + segDeltaLon * segDeltaLon;

        // If segment has zero length, return distance to start point
        if (segmentLengthSq < 1e-10) {
            return calculatePointDistance(pointLat, pointLon, segStartLat, segStartLon);
        }

        // Calculate projection parameter t
        double t = (pointDeltaLat * segDeltaLat + pointDeltaLon * segDeltaLon) / segmentLengthSq;

        // Clamp t to [0,1] to stay within the segment
        t = Math.max(0, Math.min(1, t));

        // Calculate closest point on segment
        double closestLat = segStartLat + t * segDeltaLat;
        double closestLon = segStartLon + t * segDeltaLon;

        // Return distance to the closest point
        return calculatePointDistance(pointLat, pointLon, closestLat, closestLon);
    }

    /**
     * Finds the closest point on an edge to the given coordinates.
     *
     * @param edge the edge ID
     * @param lat latitude of the target point
     * @param lon longitude of the target point
     * @return closest point on the edge
     */
    public GHPoint3D findClosestPointOnEdge(Integer edge, double lat, double lon) {
        EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);
        PointList pointList = state.fetchWayGeometry(FetchMode.ALL);

        // If no geometry points found, fallback to node coordinates
        if (pointList.isEmpty()) {
            // Use base node as fallback
            int nodeId = state.getBaseNode();
            return new GHPoint3D(nodeAccess.getLat(nodeId), nodeAccess.getLon(nodeId), 0);
        }

        GHPoint3D closestPoint = null;
        double minDistance = Double.MAX_VALUE;

        // Check distance to all geometry points
        for (GHPoint3D point : pointList) {
            double dist = calculatePointDistance(lat, lon, point.lat, point.lon);
            if (dist < minDistance) {
                minDistance = dist;
                closestPoint = point;
            }
        }

        // If for some reason we didn't find the closest point, use the first one
        if (closestPoint == null) {
            closestPoint = pointList.get(0);
        }

        return closestPoint;
    }
}
