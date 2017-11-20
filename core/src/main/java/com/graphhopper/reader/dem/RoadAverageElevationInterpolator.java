package com.graphhopper.reader.dem;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

public class RoadAverageElevationInterpolator extends RoadElevationInterpolator {

    private final static int MIN_GEOMETRY_SIZE = 3;
    // The max amount of points to go left and right
    private final static int MAX_SEARCH_RADIUS = 8;
    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 180;

    @Override
    protected void smooth(AllEdgesIterator edge) {
        PointList geometry = edge.fetchWayGeometry(3);
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
            //Remove the Tower Nodes
            PointList pillarNodesPointList = geometry.copy(1, geometry.size() - 1);
            edge.setWayGeometry(pillarNodesPointList);
        }
    }
}
