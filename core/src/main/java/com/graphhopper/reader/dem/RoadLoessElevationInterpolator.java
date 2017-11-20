package com.graphhopper.reader.dem;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.apache.commons.math3.analysis.interpolation.DividedDifferenceInterpolator;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;

public class RoadLoessElevationInterpolator extends RoadElevationInterpolator {

    private final static int MIN_GEOMETRY_SIZE = 4;
    // The max amount of points to go left and right
    private final static int MAX_SEARCH_RADIUS = 8;
    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 180;

    @Override
    protected void smooth(AllEdgesIterator edge) {
        PointList geometry = edge.fetchWayGeometry(3);
        if (geometry.size() >= MIN_GEOMETRY_SIZE) {

            double[] xVals = new double[geometry.size()];
            double[] yVals = new double[geometry.size()];

            double curDist = 0;

            for (int i = 0; i < geometry.size(); i++) {
                xVals[i] = curDist;
                yVals[i] = geometry.getElevation(i);

                if (i + 1 < geometry.size()) {
                    double tmp = Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(i + 1), geometry.getLon(i + 1));
                    // Loess Interpolator requires a strict monotonic sequence
                    if (tmp == 0)
                        tmp += 1;
                    curDist += tmp;
                }
            }

            double[] smoothedYVals = new LoessInterpolator(0.5, LoessInterpolator.DEFAULT_ROBUSTNESS_ITERS, LoessInterpolator.DEFAULT_ACCURACY).smooth(xVals, yVals);

            for (int i = 0; i < smoothedYVals.length; i++) {
                geometry.setElevation(i, smoothedYVals[i]);
            }

            //Remove the Tower Nodes
            PointList pillarNodesPointList = geometry.copy(1, geometry.size() - 1);
            edge.setWayGeometry(pillarNodesPointList);
        }
    }
}
