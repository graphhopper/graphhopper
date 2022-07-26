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
    public static void smoothWindow(PointList geometry) {
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
     * This method removes elevation fluctuations up to maxElevationDelta. Compared to the smoothWindow function this
     * method has the big advantage that the maximum slope of a PointList never increases (max(abs(slope_i))).
     * The disadvantage is that the appearance might be still more spiky as a result when a bigger positive slope
     * changes to a bigger negative slope.
     * <p>
     * The underlying algorithm is an adapted Ramer-Douglas-Peucker algorithm (see #2634) with a maximum elevation change and:
     * 1. here we try to remove the elevation fluctuation and ignore any lat,lon difference
     * 2. instead of removing the point the elevation will be calculated from the average slope of the first and last point
     */
    public static void smoothRamer(PointList points, double maxElevationDelta) {
        internSmoothRamer(points, 0, points.size() - 1, maxElevationDelta);
    }

    static void internSmoothRamer(PointList points, int fromIndex, int lastIndex, double maxElevationDelta) {
        if (lastIndex - fromIndex < 2)
            return;

        double prevLat = points.getLat(fromIndex);
        double prevLon = points.getLon(fromIndex);
        double dist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, points.getLat(lastIndex), points.getLon(lastIndex));
        double averageSlope = (points.getEle(lastIndex) - points.getEle(fromIndex)) / dist2D;
        double prevAverageSlopeEle = points.getEle(fromIndex);
        double maxEleDelta = -1;
        int indexWithMaxDelta = -1;
        for (int i = fromIndex + 1; i < lastIndex; i++) {
            double lat = points.getLat(i);
            double lon = points.getLon(i);
            double ele = points.getEle(i);
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

        if (indexWithMaxDelta < 0)
            throw new IllegalStateException("maximum not found in [" + fromIndex + "," + lastIndex + "] " + points);

        // the maximum elevation change limit filters away especially the smaller high frequent elevation changes,
        // which is likely the "noise" that we want to remove.
        if (maxElevationDelta > maxEleDelta) {
            prevLat = points.getLat(fromIndex);
            prevLon = points.getLon(fromIndex);
            prevAverageSlopeEle = points.getEle(fromIndex);
            for (int i = fromIndex + 1; i < lastIndex; i++) {
                double lat = points.getLat(i);
                double lon = points.getLon(i);
                double tmpDist2D = DistanceCalcEarth.DIST_EARTH.calcDist(prevLat, prevLon, lat, lon);
                double eleFromAverageSlope = averageSlope * tmpDist2D + prevAverageSlopeEle;
                points.setElevation(i, eleFromAverageSlope);
                prevAverageSlopeEle = eleFromAverageSlope;
                prevLat = lat;
                prevLon = lon;
            }
        } else {
            internSmoothRamer(points, fromIndex, indexWithMaxDelta, maxElevationDelta);
            internSmoothRamer(points, indexWithMaxDelta, lastIndex, maxElevationDelta);
        }
    }
}
