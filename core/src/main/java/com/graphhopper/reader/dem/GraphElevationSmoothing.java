package com.graphhopper.reader.dem;

import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;

/**
 * This class smooths the elevation data of a PointList by calculating the average elevation over
 * multiple points of that PointList.
 * <p>
 * The ElevationData is read from rectangular tiles. Especially when going along a cliff,
 * valley, or pass, it can happen that a small part of the road contains incorrect elevation data.
 * This is because the elevation data is coarse and sometimes contains errors.
 * <p>
 * This can lead to incorrect ascend, descend, and distance calculation of a route.
 *
 * @author Robin Boldt
 */
public class GraphElevationSmoothing {

    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 150;

    public static PointList smoothElevation(PointList geometry) {
        for (int i = 1; i < geometry.size() - 1; i++) {

            int start = i;
            for (int j = i-1; j >= 0 ; j--) {
                if (MAX_SEARCH_DISTANCE > Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    start = j;
                }else{
                    break;
                }
            }

            int end = i;
            for (int j = i+1; j < geometry.size(); j++) {
                if (MAX_SEARCH_DISTANCE > Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    // +1 because the end is exclusive
                    end = j+1;
                }else{
                    break;
                }
            }

            // In this case we cannot find any points withing the max search distance, so we simply skip this point
            if(start == end)
                continue;

            double sum = 0;
            for (int j = start; j < end; j++) {
                // We skip points that are too far away, important for motorways
                if (MAX_SEARCH_DISTANCE > Helper.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    sum += geometry.getEle(j);
                }
            }
            double smoothed = sum / (end-start);
            geometry.setElevation(i, smoothed);
        }
        return geometry;
    }

}
