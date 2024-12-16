package com.graphhopper.reader.dem;

import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.PointList;

/**
 * Elevation data is read from DEM tiles that have data points for rectangular tiles usually having an
 * edge length of 30 or 90 meter. Elevation in between the middle points of those tiles will be
 * interpolated and weighted by the distance from a node to adjacent tile centers.
 * <p>
 * Ways that go along cliffs or ridges are particularly affected by ups and downs that do not reflect
 * the actual elevation but may be artifacts originated from very accurately mapping when elevation has
 * a lower resolution.
 *
 * @author Peter Karich
 */
public class EdgeElevationSmoothingRamer {
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
    public static void smooth(PointList pointList, double maxElevationDelta) {
        internSmooth(pointList, 0, pointList.size() - 1, maxElevationDelta);
    }

    static void internSmooth(PointList pointList, int fromIndex, int lastIndex, double maxElevationDelta) {
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
            internSmooth(pointList, fromIndex, indexWithMaxDelta, maxElevationDelta);
            internSmooth(pointList, indexWithMaxDelta, lastIndex, maxElevationDelta);
        }
    }
}
