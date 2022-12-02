package com.graphhopper.reader.dem;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.PointList;

/**
 * The ElevationData is read from rectangular tiles. Especially when going along a cliff,
 * valley, or pass, it can happen that a small part of the road contains incorrect elevation data.
 * This is because the elevation data is coarse and sometimes contains errors.
 *
 * @author Robin Boldt
 */
public class EdgeElevationSmoothing {

    // If the point is farther then this, we stop averaging
    private final static int MAX_SEARCH_DISTANCE = 150;

    /**
     * This method smooths the elevation data of a PointList by calculating the average elevation over
     * multiple points of that PointList.
     */
    public static void smoothMovingAverage(PointList geometry) {
        for (int i = 1; i < geometry.size() - 1; i++) {

            int start = i;
            for (int j = i - 1; j >= 0; j--) {
                if (MAX_SEARCH_DISTANCE > DistancePlaneProjection.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    start = j;
                } else {
                    break;
                }
            }

            int end = i;
            for (int j = i + 1; j < geometry.size(); j++) {
                if (MAX_SEARCH_DISTANCE > DistancePlaneProjection.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    // +1 because the end is exclusive
                    end = j + 1;
                } else {
                    break;
                }
            }

            // In this case we cannot find any points within the max search distance, so we simply skip this point
            if (start == end)
                continue;

            double sum = 0;
            for (int j = start; j < end; j++) {
                // We skip points that are too far away, important for motorways
                if (MAX_SEARCH_DISTANCE > DistancePlaneProjection.DIST_PLANE.calcDist(geometry.getLat(i), geometry.getLon(i), geometry.getLat(j), geometry.getLon(j))) {
                    sum += geometry.getEle(j);
                }
            }
            double smoothed = sum / (end - start);
            geometry.setElevation(i, smoothed);
        }
    }

    /**
     * This method removes elevation fluctuations up to maxElevationDelta. Compared to the smoothMovingAverage function
     * this method has the advantage that the maximum slope of a PointList never increases (max(abs(slope_i))).
     * The disadvantage is that the appearance might be still more spiky (at tower nodes) as a result when a bigger
     * positive slope changes to a bigger negative slope.
     * <p>
     * The underlying algorithm is an adapted Ramer-Douglas-Peucker algorithm (see #2634) with a maximum elevation change and:
     * 1. only elevation changes are considered and any lat,lon difference is ignored
     * 2. instead of removing the point the elevation will be calculated from the average slope of the first and last
     * point of the specified pointList
     */
    public static void smoothRamer(PointList pointList, double maxElevationDelta) {
        internSmoothRamer(pointList, 0, pointList.size() - 1, maxElevationDelta);
    }

    static void internSmoothRamer(PointList pointList, int fromIndex, int lastIndex, double maxElevationDelta) {
        if (lastIndex - fromIndex < 2)
            return;

        double prevLat = pointList.getLat(fromIndex);
        double prevLon = pointList.getLon(fromIndex);
        double dist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, pointList.getLat(lastIndex), pointList.getLon(lastIndex));

        // in rare cases the first point can be identical to the last for e.g. areas (or for things like man_made=pier which are not explicitly excluded from adding edges)
        double averageSlope = dist2D == 0 ? 0 : (pointList.getEle(lastIndex) - pointList.getEle(fromIndex)) / dist2D;
        double prevAverageSlopeEle = pointList.getEle(fromIndex);
        double maxEleDelta = -1;
        int indexWithMaxDelta = -1;
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            double lat = pointList.getLat(i);
            double lon = pointList.getLon(i);
            double ele = pointList.getEle(i);
            double tmpDist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, lat, lon);
            double eleFromAverageSlope = averageSlope * tmpDist2D + prevAverageSlopeEle;
            double tmpEleDelta = Math.abs(ele - eleFromAverageSlope);
            if (maxEleDelta < tmpEleDelta) {
                indexWithMaxDelta = i;
                maxEleDelta = tmpEleDelta;
            }
            prevAverageSlopeEle = eleFromAverageSlope;
            prevLat = lat;
            prevLon = lon;
        }

        // the maximum elevation change limit filters away especially the smaller high frequent elevation changes,
        // which is likely the "noise" that we want to remove.
        if (indexWithMaxDelta < 0 || maxElevationDelta > maxEleDelta) {
            prevLat = pointList.getLat(fromIndex);
            prevLon = pointList.getLon(fromIndex);
            prevAverageSlopeEle = pointList.getEle(fromIndex);
            for (int i = fromIndex + 1; i < lastIndex; i++) {
                double lat = pointList.getLat(i);
                double lon = pointList.getLon(i);
                double tmpDist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, lat, lon);
                double eleFromAverageSlope = averageSlope * tmpDist2D + prevAverageSlopeEle;
                pointList.setElevation(i, eleFromAverageSlope);
                prevAverageSlopeEle = eleFromAverageSlope;
                prevLat = lat;
                prevLon = lon;
            }
        } else {
            internSmoothRamer(pointList, fromIndex, indexWithMaxDelta, maxElevationDelta);
            internSmoothRamer(pointList, indexWithMaxDelta, lastIndex, maxElevationDelta);
        }
    }
}
