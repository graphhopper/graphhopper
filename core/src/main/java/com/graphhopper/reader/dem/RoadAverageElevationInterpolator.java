package com.graphhopper.reader.dem;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

import java.util.ArrayList;
import java.util.List;

public class RoadAverageElevationInterpolator extends RoadElevationInterpolator {

    private final static int MIN_GEOMETRY_SIZE = 3;
    // The max amount of points to go left and right
    private final static int MAX_SEARCH_RADIUS = 8;
    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 180;

    // Don't go too far, otherwise the influence might be too big
    private final static int MAX_TOWER_DISTANCE = 30;

    @Override
    public PointList smoothElevation(PointList geometry) {
        if (geometry.size() >= MIN_GEOMETRY_SIZE) {
            for (int i = 1; i < geometry.size() - 1; i++) {
                int start = i - MAX_SEARCH_RADIUS < 0 ? 0 : i - MAX_SEARCH_RADIUS;
                // +1 because we check for "< end"
                int end = i + MAX_SEARCH_RADIUS + 1 >= geometry.size() ? geometry.size() : i + MAX_SEARCH_RADIUS + 1;
                double sum = 0;
                int counter = 0;
                for (int j = start; j < end; j++) {
                    // We skip points that are too far away, important for motorways
                    if (MAX_SEARCH_DISTANCE > Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                        sum += geometry.getEle(j);
                        counter++;
                    }
                }
                double smoothed = sum / counter;
                geometry.setElevation(i, smoothed);
            }
        }
        return geometry;
    }

    @Override
    protected void smoothPillarNodesOfEdge(AllEdgesIterator edge) {
        PointList geometry = edge.fetchWayGeometry(3);
        if (geometry.size() >= MIN_GEOMETRY_SIZE) {
            geometry = smoothElevation(geometry);
            //Remove the Tower Nodes
            PointList pillarNodesPointList = geometry.copy(1, geometry.size() - 1);
            edge.setWayGeometry(pillarNodesPointList);
        }
    }

    @Override
    protected void smoothTowerNode(Graph graph, int nodeId) {
        EdgeIterator iterator = graph.createEdgeExplorer().setBaseNode(nodeId);
        List<Double> heights = new ArrayList<>();

        NodeAccess na = graph.getNodeAccess();
        final double lat = na.getLat(nodeId);
        final double lon = na.getLon(nodeId);

        while (iterator.next()) {
            PointList geometry = iterator.fetchWayGeometry(2);
            if (!geometry.isEmpty() && MAX_TOWER_DISTANCE > Helper.DIST_PLANE.calcDist(lat, lon, geometry.getLat(0), geometry.getLon(0))) {
                heights.add(geometry.getEle(0));
            }
        }

        if (heights.size() < 2) {
            return;
        }

        double origHeight = na.getElevation(nodeId);

        //Only try to smooth if it's an outlier
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double d : heights) {
            if (d < min) {
                min = d;
            }
            if (d > max) {
                max = d;
            }
        }

        double avg;

        // All surrounding points are larger
        if (min < 10000 && min > origHeight)
            avg = min;
            // All sourrounding points are smaller
        else if (max > -10000 && max < origHeight)
            avg = max;
        else
            return;

        double newHeight = (origHeight + avg) / 2;

        // TODO - remove this
        if (Math.abs(origHeight - newHeight) > 10)
            System.out.println("Smoothed Towernode from " + origHeight + " to " + newHeight + " at " + na.getLat(nodeId) + "," + na.getLon(nodeId));

        na.setNode(nodeId, na.getLat(nodeId), na.getLon(nodeId), newHeight);
    }
}
